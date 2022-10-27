package co.casterlabs.rakurai.impl.http.nano;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.casterlabs.rakurai.StringUtil;
import co.casterlabs.rakurai.io.http.DropConnectionException;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.HttpResponse.ResponseContent;
import co.casterlabs.rakurai.io.http.HttpStatus;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import co.casterlabs.rakurai.io.http.websocket.WebsocketListener;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoWSD;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class NanoHttpServer extends NanoWSD implements HttpServer {
    private FastLogger logger = new FastLogger("Rakurai NanoHttpServer");
    private HttpListener server;
    private boolean secure;

    private HttpServerBuilder config;

    static {
        try {
            Field field = NanoHTTPD.class.getDeclaredField("LOG");

            field.setAccessible(true);

            Logger log = (Logger) field.get(null);

            log.setLevel(Level.OFF); // Hush
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
    }

    public NanoHttpServer(HttpListener server, String hostname, int port, HttpServerBuilder config) {
        super(hostname, port);

        this.setAsyncRunner(new NanoRunner());

        this.secure = false;
        this.server = server;
        this.config = config;
    }

    public NanoHttpServer(HttpListener server, String hostname, int port, WrappedSSLSocketFactory factory, String[] tls, HttpServerBuilder config) {
        super(hostname, port);

        this.makeSecure(factory, tls);
        this.setAsyncRunner(new NanoRunner());

        this.secure = true;
        this.server = server;
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        this.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    // Serves http sessions or calls super to serve websockets
    @SneakyThrows
    @SuppressWarnings("deprecation")
    @Override
    public Response serve(IHTTPSession nanoSession) {
        if (this.isWebsocketRequested(nanoSession)) {
            return super.serve(nanoSession);
        } else {
            long start = System.currentTimeMillis();
            NanoHttpSession session = new NanoHttpSession(nanoSession, logger, this.getListeningPort(), this.config);

            HttpResponse response = this.server.serveSession(session.getHost(), session, this.secure);

            if (response == null) {
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_IMPLEMENTED, "text/plaintext", "");
            } else if (response.getStatus() == StandardHttpStatus.NO_RESPONSE) {
                logger.debug("Dropped HTTP %s %s %s", session.getMethod().name(), session.getRemoteIpAddress(), session.getHost() + session.getUri());
                throw new DropConnectionException();
            } else {
                response.finalizeResult(session, this.config, this.logger);

                String mime = response.getAllHeaders().getOrDefault("content-type", "text/plaintext");

                IStatus status = convertStatus(response.getStatus());
                ResponseContent content = response.getContent();

                ByteArrayOutputStream responseSink;

                long length = content.getLength();
                if (length >= 0) {
                    responseSink = new ByteArrayOutputStream((int) length);
                } else {
                    responseSink = new ByteArrayOutputStream();
                }

                content.write(responseSink);
                InputStream responseStream = new ByteArrayInputStream(responseSink.toByteArray());

                Response nanoResponse;
                if (length >= 0) {
                    nanoResponse = NanoHTTPD.newFixedLengthResponse(
                        status,
                        mime,
                        responseStream,
                        content.getLength()
                    );
                } else {
                    nanoResponse = NanoHTTPD.newChunkedResponse(status, mime, responseStream);
                }

                for (Map.Entry<String, String> header : response.getAllHeaders().entrySet()) {
                    // Check prevents duplicate headers
                    if (!header.getKey().equalsIgnoreCase("content-type") && !header.getKey().equalsIgnoreCase("content-length")) {
                        String key = StringUtil.prettifyHeader(header.getKey());
                        String value = header.getValue();

                        nanoResponse.addHeader(key, value);
                    }
                }

                double time = (System.currentTimeMillis() - start) / 1000d;
                logger.debug("Served HTTP %s %s %s (%.2fs)", session.getMethod().name(), session.getRemoteIpAddress(), session.getHost() + session.getUri(), time);

                return nanoResponse;
            }
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession nanoSession) {
        long start = System.currentTimeMillis();
        NanoWebsocketSessionWrapper session = new NanoWebsocketSessionWrapper(nanoSession, this.getListeningPort(), this.config);

        WebsocketListener listener = this.server.serveWebsocketSession(session.getHost(), session, this.secure);

        if (listener != null) {
            double time = (System.currentTimeMillis() - start) / 1000d;
            logger.debug("Served websocket %s %s (%.2fs)", session.getRemoteIpAddress(), session.getHost() + session.getUri(), time);

            return new NanoWebsocketWrapper(nanoSession, listener, session);
        } else {
            logger.debug("Dropped websocket %s %s", session.getRemoteIpAddress(), session.getHost() + session.getUri());
            throw new DropConnectionException();
        }
    }

    @Override
    public int getPort() {
        return this.getListeningPort();
    }

    private static IStatus convertStatus(HttpStatus status) {
        return new IStatus() {
            @Override
            public String getDescription() {
                return status.getStatusString(); // What the hell Nano
            }

            @Override
            public int getRequestStatus() {
                return status.getStatusCode();
            }
        };
    }

    @Override
    public HttpServerImplementation getImplementation() {
        return HttpServerImplementation.NANO;
    }

}
