package co.casterlabs.rakurai.impl.http.nano;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.casterlabs.rakurai.io.http.DropConnectionException;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.HttpResponse.TransferEncoding;
import co.casterlabs.rakurai.io.http.HttpStatus;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import co.casterlabs.rakurai.io.http.websocket.WebsocketListener;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoWSD;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class NanoHttpServer extends NanoWSD implements HttpServer {
    private FastLogger logger = new FastLogger();
    private HttpListener server;
    private boolean secure;

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

    public NanoHttpServer(HttpListener server, int port) {
        super(port);

        this.setAsyncRunner(new NanoRunner());

        this.secure = false;
        this.server = server;
    }

    public NanoHttpServer(HttpListener server, int port, WrappedSSLSocketFactory factory, String[] tls) {
        super(port);

        this.makeSecure(factory, tls);
        this.setAsyncRunner(new NanoRunner());

        this.secure = true;
        this.server = server;
    }

    @Override
    public void start() throws IOException {
        this.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    // Serves http sessions or calls super to serve websockets
    @Override
    public Response serve(IHTTPSession nanoSession) {
        if (this.isWebsocketRequested(nanoSession)) {
            try {
                return super.serve(nanoSession);
            } catch (NullPointerException e) { // Happens when nothing sets the websocket response listener.
                throw new DropConnectionException();
            }
        } else {
            long start = System.currentTimeMillis();
            NanoHttpSession session = new NanoHttpSession(nanoSession, logger, this.getListeningPort());

            HttpResponse response = this.server.serveSession(session.getHost(), session, this.secure);

            if (response == null) {
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_IMPLEMENTED, "text/plaintext", "");
            } else if (response.getStatus() == StandardHttpStatus.NO_RESPONSE) {
                logger.debug("Dropped HTTP %s %s %s", session.getMethod().name(), session.getRemoteIpAddress(), session.getHost() + session.getUri());
                throw new DropConnectionException();
            } else {
                String mime = response.getAllHeaders().getOrDefault("content-type", "text/plaintext");

                //@formatter:off
                IStatus status = convertStatus(response.getStatus());
                Response nanoResponse = (response.getMode() == TransferEncoding.CHUNKED) ?
                        NanoHTTPD.newChunkedResponse(status, mime, response.getResponseStream()) :
                        NanoHTTPD.newFixedLengthResponse(status, mime, response.getResponseStream(), response.getLength());
                //@formatter:on

                for (Map.Entry<String, String> header : response.getAllHeaders().entrySet()) {
                    if (!header.getKey().equalsIgnoreCase("content-type") && !header.getKey().equalsIgnoreCase("content-length")) {
                        nanoResponse.addHeader(header.getKey(), header.getValue()); // Check prevents duplicate headers
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
        NanoWebsocketSessionWrapper session = new NanoWebsocketSessionWrapper(nanoSession, this.getListeningPort());

        WebsocketListener listener = this.server.serveWebsocketSession(session.getHost(), session, this.secure);

        if (listener != null) {
            double time = (System.currentTimeMillis() - start) / 1000d;
            logger.debug("Served websocket %s %s (%.2fs)", session.getRemoteIpAddress(), session.getHost() + session.getUri(), time);

            return new NanoWebsocketWrapper(nanoSession, listener);
        } else {
            logger.debug("Dropped websocket %s %s", session.getRemoteIpAddress(), session.getHost() + session.getUri());
            return null;
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
