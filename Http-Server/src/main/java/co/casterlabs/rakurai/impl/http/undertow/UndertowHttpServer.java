package co.casterlabs.rakurai.impl.http.undertow;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.xnio.Options;
import org.xnio.Sequence;

import co.casterlabs.rakurai.StringUtil;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.Debugging;
import co.casterlabs.rakurai.io.http.DropConnectionException;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.HttpResponse.ResponseContent;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import co.casterlabs.rakurai.io.http.websocket.BinaryWebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.TextWebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.Websocket;
import co.casterlabs.rakurai.io.http.websocket.WebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.WebsocketListener;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import lombok.Getter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@SuppressWarnings("deprecation")
public class UndertowHttpServer implements HttpServer, HttpHandler, WebSocketConnectionCallback {
    private @Getter FastLogger logger = new FastLogger("Rakurai UndertowHttpServer");
    private Undertow undertow;
    private HttpListener server;
    private int port;

    private boolean running = false;
    private boolean secure = false;

    private HttpServerBuilder config;

    static {
        System.setProperty("org.jboss.logging.provider", "slf4j"); // This mutes it.
    }

    private Undertow.Builder makeBuilder(HttpListener server, String hostname, int port, HttpServerBuilder builder) {
        return Undertow.builder()
            .setServerOption(UndertowOptions.ENABLE_SPDY, builder.isSPDYEnabled())
            .setServerOption(UndertowOptions.ENABLE_HTTP2, builder.isHttp2Enabled())
            .setServerOption(UndertowOptions.DECODE_URL, false)
//            .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false)

            .setBufferSize(IOUtil.DEFAULT_BUFFER_SIZE)
            .setDirectBuffers(false)

            .setHandler(Handlers.websocket(this, this));
    }

    public UndertowHttpServer(HttpListener server, String hostname, int port, HttpServerBuilder builder) {
        this.undertow = makeBuilder(server, hostname, port, builder)
            .addHttpListener(port, hostname)
            .build();

        this.port = port;
        this.server = server;
        this.config = builder;
    }

    public UndertowHttpServer(HttpListener server, String hostname, int port, KeyManager[] keyManagers, TrustManager[] trustManagers, String[] tls, List<String> cipherSuites, HttpServerBuilder builder) {
        this.undertow = makeBuilder(server, hostname, port, builder)
            .setSocketOption(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(cipherSuites))
            .setSocketOption(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(tls))

            .addHttpsListener(port, hostname, keyManagers, trustManagers)
            .build();

        this.port = port;
        this.secure = true;
        this.server = server;
        this.config = builder;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        long start = System.currentTimeMillis();
        HttpSession session = new UndertowHttpSessionWrapper(exchange, this.port, this.config, this.logger);
        HttpResponse response = null;

        session.getLogger().debug(
            "Processing http %s request for %s, resource: %s%s",
            session.getMethod().name(), session.getRemoteIpAddress(), session.getHost(), session.getUri()
        );

        try {
            exchange.startBlocking();
            response = this.server.serveSession(session.getHost(), session, this.secure);

            if (response == null) {
                response = HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_IMPLEMENTED);
                session.getLogger().debug("No response, returning NOT_IMPLEMENTED.");
            }

            if (response.getStatus() == StandardHttpStatus.NO_RESPONSE) {
                session.getLogger().debug("Got 444 (NO_RESPONSE), dropping request.");
                throw new DropConnectionException();
            }

            session.getLogger().debug("Response status: %d (%s)", response.getStatus().getStatusCode(), response.getStatus().getDescription());
            exchange.setStatusCode(response.getStatus().getStatusCode());
            exchange.setReasonPhrase(response.getStatus().getDescription());

            // Write out the response headers.
            for (Map.Entry<String, String> entry : response.getAllHeaders().entrySet()) {
                String key = StringUtil.prettifyHeader(entry.getKey());
                String value = entry.getValue();

                exchange.getResponseHeaders().add(HttpString.tryFromString(key), value);
            }

            ResponseContent content = response.getContent();
            OutputStream out = exchange.getOutputStream();

            // If it's a fixed-length response we want to add that info.
            long length = content.getLength();
            if (length >= 0) {
                session.getLogger().debug("Response transport is fixed-length. (len=%,d)", length);
                exchange.setResponseContentLength(length);
            } else {
                session.getLogger().debug("Response transport is chunked.");
            }

            content.write(out);

            long time = System.currentTimeMillis() - start;
            this.logger.debug("Successfully served request in %,dms.", time);
        } catch (DropConnectionException e) {
            logger.debug("Dropped request.");
            try {
                exchange.getConnection().close();
            } catch (IOException ignored) {}
        } catch (Exception e) {
            if (e.getMessage() != null) {
                String message = e.getMessage();
                if (message.contains("Stream is closed") ||
                    message.contains("connection was aborted") ||
                    message.contains("reset by peer") ||
                    message.contains("Broken pipe")) {
                    return;
                }
            }

            session.getLogger().severe("An exception occurred whilst handling request:\n%s", e);

            if (exchange.isResponseStarted()) {
                // We've already started writing the request, bail out.
                IOUtil.safeClose(exchange.getConnection());
            } else {
                exchange.setStatusCode(StandardHttpStatus.INTERNAL_ERROR.getStatusCode());
                exchange.setReasonPhrase(StandardHttpStatus.INTERNAL_ERROR.getDescription());
                exchange.setResponseContentLength(0);
            }
        } finally {
            Debugging.finalizeResult(response, session, this.config, this.logger);

            // We might've already forcibly closed the connection.
            if (exchange.getConnection().isOpen()) {
                exchange.endExchange();
            }

            if (response != null) {
                IOUtil.safeClose(response.getContent());
            }
        }
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        long start = System.currentTimeMillis();
        WebsocketSession session = new UndertowWebsocketSessionWrapper(exchange, channel, this.port, this.config, this.logger);

        session.getLogger().debug(
            "Processing websocket request for %s, resource: %s%s",
            session.getRemoteIpAddress(), session.getHost(), session.getUri()
        );

        try {
            WebsocketListener listener = this.server.serveWebsocketSession(session.getHost(), session, this.secure);

            if (listener == null) {
                session.getLogger().debug("No listener provided, dropping request.");
                IOUtil.safeClose(exchange);
                return;
            }

            session.getLogger().debug("Got listener, attaching.");
            Websocket websocket = new UndertowWebsocketChannelWrapper(channel, session);

            boolean logWebsocketFrames = System.getProperty("rakurailogwebsocketframes", "").equals("true");
            session.getLogger().debug("-Drakurailogwebsocketframes=%b", logWebsocketFrames);

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    WebsocketFrame frame = new TextWebsocketFrame(message.getData());

                    if (logWebsocketFrames) {
                        session.getLogger().trace("WebsocketFrame (%s):\n%s", websocket.getSession().getRemoteIpAddress(), frame);
                    }

                    listener.onFrame(websocket, frame);
                }

                @Override
                protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
                    try {
                        byte[] bytes = WebSockets
                            .mergeBuffers(message.getData().getResource())
                            .array();
                        WebsocketFrame frame = new BinaryWebsocketFrame(bytes);

                        if (logWebsocketFrames) {
                            session.getLogger().trace("WebsocketFrame (%s):\n%s", websocket.getSession().getRemoteIpAddress(), frame);
                        }

                        listener.onFrame(websocket, frame);
                    } finally {
                        // Always free the buffer no matter what.
                        message.getData().free();
                    }
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
                    session.getLogger().debug("Closed WebSocket session.");
                    Debugging.finalizeResult(null, session, config, logger);

                    try {
                        listener.onClose(websocket);
                    } catch (Exception ignored) {}
                    webSocketChannel.sendClose();
                }

                @Override
                protected void onError(WebSocketChannel channel, Throwable t) {
                    session.getLogger().severe("Uncaught:\n%s", t);
                }
            });

            try {
                listener.onOpen(websocket);
            } catch (Throwable t) {
                session.getLogger().severe("Uncaught:\n%s", t);

                // An error occurred, close the connection immediately.
                IOUtil.safeClose(channel);

                try {
                    // Attempt to tell the listener that we've closed the socket.
                    // May not work as this is technically a "broken" state.
                    listener.onClose(websocket);
                } catch (Throwable ignored) {}
                return;
            }

            long time = System.currentTimeMillis() - start;
            this.logger.debug("Successfully served request in %,dms.", time);

            session.getLogger().debug("Processing frames...");
            channel.resumeReceives();
        } catch (Exception e) {
            session.getLogger().severe("An exception occurred whilst handling request:\n%s", e);
            Debugging.finalizeResult(null, session, this.config, this.logger);
        }
    }

    @Override
    public void start() {
        this.undertow.start();
        this.running = true;
    }

    @Override
    public void stop() {
        this.undertow.stop();
        this.running = false;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public boolean isAlive() {
        return this.running;
    }

    @Override
    public HttpServerImplementation getImplementation() {
        return HttpServerImplementation.UNDERTOW;
    }

}
