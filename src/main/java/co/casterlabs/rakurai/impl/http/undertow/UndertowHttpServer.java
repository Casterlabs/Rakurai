package co.casterlabs.rakurai.impl.http.undertow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.xnio.Options;
import org.xnio.Sequence;

import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.DropConnectionException;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.HttpResponse.TransferEncoding;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import co.casterlabs.rakurai.io.http.websocket.Websocket;
import co.casterlabs.rakurai.io.http.websocket.WebsocketListener;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class UndertowHttpServer implements HttpServer, HttpHandler, WebSocketConnectionCallback {
    private FastLogger logger = new FastLogger();
    private Undertow undertow;
    private HttpListener server;
    private int port;

    private boolean running = false;
    private boolean secure = false;

    static {
        System.setProperty("org.jboss.logging.provider", "slf4j"); // This mutes it.
    }

    @SuppressWarnings("deprecation")
    public UndertowHttpServer(HttpListener server, String hostname, int port, HttpServerBuilder builder) {
        //@formatter:off
        this.undertow = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_SPDY, builder.isSPDYEnabled())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, builder.isHttp2Enabled())

                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false)

                .setHandler(new BlockingHandler(Handlers.websocket(this, this)))
                .addHttpListener(port, hostname)
                .build();
        //@formatter:on

        this.port = port;
        this.server = server;
    }

    @SuppressWarnings("deprecation")
    public UndertowHttpServer(HttpListener server, String hostname, int port, KeyManager[] keyManagers, TrustManager[] trustManagers, String[] tls, List<String> cipherSuites, HttpServerBuilder builder) {
        //@formatter:off
        this.undertow = Undertow.builder()
                .setSocketOption(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(cipherSuites))
                .setSocketOption(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(tls))

                .setServerOption(UndertowOptions.ENABLE_SPDY, builder.isSPDYEnabled())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, builder.isHttp2Enabled())

                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false)

                .setHandler(new BlockingHandler(Handlers.websocket(this, this)))
                .addHttpsListener(port, hostname, keyManagers, trustManagers)
                .build();
        //@formatter:on

        this.port = port;
        this.secure = true;
        this.server = server;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.dispatch(() -> {
            try {
                long start = System.currentTimeMillis();

                HttpSession session = new UndertowHttpSessionWrapper(exchange, this.port);
                HttpResponse response = this.server.serveSession(session.getHost(), session, this.secure);

                if (response == null) {
                    exchange.setStatusCode(StandardHttpStatus.NOT_IMPLEMENTED.getStatusCode());
                    exchange.setReasonPhrase(StandardHttpStatus.NOT_IMPLEMENTED.getDescription());
                } else if (response.getStatus() == StandardHttpStatus.NO_RESPONSE) {
                    IOUtil.safeClose(exchange.getConnection());
                    return;
                } else {
                    exchange.setStatusCode(response.getStatus().getStatusCode());
                    exchange.setReasonPhrase(response.getStatus().getDescription());

                    for (Map.Entry<String, String> entry : response.getAllHeaders().entrySet()) {
                        exchange.getResponseHeaders().add(HttpString.tryFromString(entry.getKey()), entry.getValue());
                    }

                    if (response.getMode() == TransferEncoding.FIXED_LENGTH) {
                        exchange.setResponseContentLength(response.getLength());
                    }

                    double time = (System.currentTimeMillis() - start) / 1000d;
                    this.logger.debug("Served HTTP %s %s %s (%.2fs)", session.getMethod().name(), session.getRemoteIpAddress(), session.getHost() + session.getUri(), time);

                    InputStream in = response.getResponseStream();
                    OutputStream out = exchange.getOutputStream();

                    IOUtil.writeInputStreamToOutputStream(in, out);
                }
            } catch (Exception e) {
                if (!(e instanceof DropConnectionException)) {
                    e.printStackTrace();
                }
            }

            IOUtil.safeClose(exchange.getConnection());
        });
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        WebsocketSession session = new UndertowWebsocketSessionWrapper(exchange, channel, this.port);
        WebsocketListener listener = this.server.serveWebsocketSession(session.getHost(), session, this.secure);

        if (listener == null) {
            IOUtil.safeClose(channel);
        } else {
            Websocket websocket = new UndertowWebsocketChannelWrapper(channel, session);

            listener.onOpen(websocket);

            channel.getReceiveSetter().set(new AbstractReceiveListener() {

                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    listener.onText(websocket, message.getData());
                }

                @SuppressWarnings("deprecation")
                @Override
                protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
                    for (ByteBuffer buffer : message.getData().getResource()) {
                        listener.onBinary(websocket, buffer.array());
                    }
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
                    try {
                        listener.onClose(websocket);
                    } catch (Exception ignored) {}

                    webSocketChannel.sendClose();
                }

                @Override
                protected void onError(WebSocketChannel channel, Throwable ignored) {}

            });

            channel.resumeReceives();
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
