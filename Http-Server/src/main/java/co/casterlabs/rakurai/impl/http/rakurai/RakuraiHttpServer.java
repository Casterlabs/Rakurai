package co.casterlabs.rakurai.impl.http.rakurai;

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
import co.casterlabs.rakurai.io.http.DropConnectionException;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
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
        FastLogger logger = this.logger.createChild(clientSocket.getInetAddress().getHostAddress() + ':' + clientSocket.getPort());
        HttpResponse response = null;

        try {
            InputStream in = clientSocket.getInputStream();

            // Set some SO flags.
            clientSocket.setTcpNoDelay(true);

            while (true) {
                HttpSession session = null;
                HttpVersion version = HttpVersion.HTTP_1_0; // Our default.

                // Catch any RHSHttpExceptions and convert them into responses.
                try {
                    session = RHSProtocol.accept(this, clientSocket, in);
                    version = session.getVersion();
                } catch (RHSHttpException e) {
                    this.logger.severe("An error occurred whilst handling a request:\n%s", e);
                    response = HttpResponse.newFixedLengthResponse(e.status);
                }

                // We have a valid session, try to serve it.
                // Note that response will always be null at this location IF session isn't.
                if (session != null) {
                    response = this.listener.serveSession(session.getHost(), session, this.isSecure);
                }

                if (response == null) throw new DropConnectionException();

                // Write out status and headers, also create the output stream for writing the
                // result.
                OutputStream out;

                switch (version) {
                    case HTTP_0_9:
                        out = clientSocket.getOutputStream();
                        break;

                    case HTTP_1_0:
                        out = clientSocket.getOutputStream();

                        // Write status.
                        writeString("HTTP/1.0 ", out);
                        writeString(response.getStatus().getStatusString(), out);
                        writeString("\r\n", out);

                        // Response content stuff.
                        long length = response.getContent().getLength();
                        if (length == -1) {
                            throw new IOException("Chunked responses are not acceptable for HTTP/1.0 requests, dropping.");
                        } else {
                            response.putHeader("Content-Length", String.valueOf(length));
                        }

                        if (!response.hasHeader("Content-Type")) {
                            response.putHeader("Content-Type", "application/octet-stream");
                        }

                        // Write headers.
                        for (Map.Entry<String, String> entry : response.getAllHeaders().entrySet()) {
                            writeString(entry.getKey(), out);
                            writeString(": ", out);
                            writeString(entry.getValue(), out);
                            writeString("\r\n", out);
                        }

                        // Write the separation line.
                        writeString("\r\n", out);
                        break;

                    case HTTP_1_1:
                    case HTTP_2_0:
                    case HTTP_3_0:
                    default:
                        throw new IOException("Unsupported HTTP version: " + session.getVersion());
                }

                // Write the response out.
                response.getContent().write(out);

                // Close OR receive another request (1.1)
                switch (version) {
                    case HTTP_0_9:
                    case HTTP_1_0:
                        return; // Close the connection.

                    case HTTP_1_1:
                    case HTTP_2_0:
                    case HTTP_3_0:
                    default:
                        break; // Handled above.
                }
            }
        } catch (DropConnectionException ignored) {
            // Automatically handled by the finally {}.
            logger.debug("Dropping connection.");
        } catch (IOException e) {
            logger.severe("An error occurred whilst handling a request:\n%s", e);
        } finally {
            if (response != null) {
                try {
                    response.getContent().close();
                } catch (IOException e) {
                    logger.severe("An error occurred whilst response content:\n%s", e);
                }
            }

            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.severe("An error occurred whilst closing a socket:\n%s", e);
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
