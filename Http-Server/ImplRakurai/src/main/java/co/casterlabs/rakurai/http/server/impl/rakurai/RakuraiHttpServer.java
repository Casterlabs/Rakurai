package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.DropConnectionException;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpResponse;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerUtil;
import co.casterlabs.rakurai.io.http.server.HttpSession;
import co.casterlabs.rakurai.io.http.server.config.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.config.HttpServerImplementation;
import lombok.Getter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@Getter
public class RakuraiHttpServer implements HttpServer {
    private final FastLogger logger = new FastLogger("Rakurai RakuraiHttpServer");

    private final HttpListener listener;
    private final HttpServerBuilder config;

    private final ExecutorService executor = Executors.newCachedThreadPool(); // TODO maybe a better one?

    private List<Socket> connectedClients = new LinkedList<>();
    private ServerSocket serverSocket;
    private boolean isSecure;

    RakuraiHttpServer(HttpListener listener, HttpServerBuilder config) {
        this.listener = listener;
        this.config = config;
    }

    private void doRead() {
        try {
            Socket clientSocket = serverSocket.accept();
            this.executor.execute(() -> this.handle(clientSocket));
        } catch (IOException e) {
            this.logger.severe("An error occurred whilst accepting a new connection:\n%s", e);
        }
    }

    private void handle(Socket clientSocket) {
        // We create a logger here so that we have one incase the connection errors
        // before the session is created. Janky IK.
        FastLogger sessionLogger = this.logger.createChild(clientSocket.getInetAddress().getHostAddress() + ':' + clientSocket.getPort());
        HttpResponse response = null;

        try {
            InputStream in = clientSocket.getInputStream();

            // Set some SO flags.
            clientSocket.setTcpNoDelay(true);

            while (true) {
                HttpSession session = null;
                HttpVersion version = HttpVersion.HTTP_1_0; // Our default.
                // Note that we don't support 2.0 or 3.0, check this in RHSProtocol.

                // Catch any RHSHttpExceptions and convert them into responses.
                try {
                    session = RHSProtocol.accept(this, clientSocket, in);
                    ((RHSHttpSession) session).postConstruct(this.config, this.logger);
                    version = session.getVersion();
                    sessionLogger = session.getLogger();
                } catch (RHSHttpException e) {
                    sessionLogger.severe("An error occurred whilst handling a request:\n%s", e);
                    response = HttpResponse.newFixedLengthResponse(e.status);
                }

                sessionLogger.debug("Using version: %s", version);

                // We have a valid session, try to serve it.
                // Note that response will always be null at this location IF session isn't.
                if (session != null) {
                    response = this.listener.serveSession(session.getHost(), session, this.isSecure);
                }

                if (response == null) throw new DropConnectionException();

                // Write out status and headers, also create the output stream for writing the
                // result.
                OutputStream out = clientSocket.getOutputStream();
                String contentEncoding = null;

                // 0.9 doesn't have a status line or anything, so we don't write it out.
                if (version.value >= 1.0) {
                    // Write status.
                    writeString(version.toString(), out);
                    writeString(" ", out);
                    writeString(response.getStatus().getStatusString(), out);
                    writeString("\r\n", out);

                    if (!response.hasHeader("Content-Type")) {
                        response.putHeader("Content-Type", "application/octet-stream");
                    }

                    // Response content stuff.
                    contentEncoding = HttpServerUtil.pickEncoding(session, response);
                    long length = response.getContent().getLength();
                    if ((length == -1) || (contentEncoding != null)) {
                        if (version.value == 1.0) {
                            throw new IOException("Chunked responses are not acceptable for HTTP/1.0 requests, dropping.");
                        }

                        response.putHeader("Transfer-Encoding", "chunked");
                    } else {
                        response.putHeader("Content-Length", String.valueOf(length));
                    }

                    sessionLogger.debug("Response headers: %s", response.getAllHeaders());

                    // Write headers.
                    for (Map.Entry<String, String> entry : response.getAllHeaders().entrySet()) {
                        writeString(entry.getKey(), out);
                        writeString(": ", out);
                        writeString(entry.getValue(), out);
                        writeString("\r\n", out);
                    }

                    // Write the separation line.
                    writeString("\r\n", out);
                }

                // Write out the response, defaulting to non-encoded responses.
                HttpServerUtil.writeWithEncoding(contentEncoding, out, response.getContent());

                if (version.value <= 1.0) {
                    return;  // Close the connection.
                }
                // Subsequent requests are handled by the while block.
            }
        } catch (DropConnectionException ignored) {
            // Automatically handled by the finally {}.
            sessionLogger.debug("Dropping connection.");
        } catch (IOException e) {
            sessionLogger.trace("An error occurred whilst handling a request:\n%s", e);
        } finally {
            if (response != null) {
                try {
                    response.getContent().close();
                } catch (IOException e) {
                    sessionLogger.severe("An error occurred whilst response content:\n%s", e);
                }
            }

            try {
                clientSocket.close();
            } catch (IOException e) {
                sessionLogger.severe("An error occurred whilst closing a socket:\n%s", e);
            }
        }
    }

    @Override
    public void start() throws IOException {
        if (this.isAlive()) return;

        try {
            this.serverSocket = new ServerSocket();
            this.serverSocket.setReuseAddress(true);
            this.serverSocket.bind(new InetSocketAddress(this.config.getHostname(), this.config.getPort()));
        } catch (IOException e) {
            this.serverSocket = null;
            throw e;
        }

        Thread acceptThread = new Thread(() -> {
            while (this.serverSocket.isBound()) {
                this.doRead();
            }
        });
        acceptThread.setName("RakuraiHttpServer - " + this.getPort());
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    @Override
    public void stop() throws IOException {
        if (!this.isAlive()) return;

        try {
            this.serverSocket.close();

            this.connectedClients.forEach((s) -> IOUtil.safeClose(s));
            this.connectedClients.clear();
        } finally {
            this.serverSocket = null;
        }
    }

    @Override
    public boolean isAlive() {
        return this.serverSocket != null;
    }

    @Override
    public int getPort() {
        return this.isAlive() ? //
            this.serverSocket.getLocalPort() : this.config.getPort();
    }

    @Override
    public HttpServerImplementation getImplementation() {
        return HttpServerImplementation.RAKURAI;
    }

    private static void writeString(String str, OutputStream out) throws IOException {
        out.write(str.getBytes(RHSProtocol.HEADER_CHARSET));
    }

}
