package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@Getter
public class RakuraiHttpServer implements HttpServer {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O");
    private static final int READ_TIMEOUT = 1;

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
            this.connectedClients.add(clientSocket);

            // Better logging format for v6 addresses :^)
            String addr = clientSocket.getInetAddress().getHostAddress();
            if (addr.indexOf(':') != -1) {
                this.logger.debug("New connection from [%s]:%d", addr, clientSocket.getPort());
            } else {
                this.logger.debug("New connection from %s:%d", addr, clientSocket.getPort());
            }

            this.executor.execute(() -> {
                this.handle(clientSocket);
                this.connectedClients.remove(clientSocket);
            });
        } catch (IOException e) {
            this.logger.severe("An error occurred whilst accepting a new connection:\n%s", e);
        }
    }

    private void handle(Socket clientSocket) {
        // We create a logger here so that we have one incase the connection errors
        // before the session is created. Janky IK.
        FastLogger sessionLogger = this.logger.createChild("<unknown session> " + clientSocket.getPort());
        sessionLogger.debug("Handling request...");

        HttpResponse response = null;

        try {
            InputStream in = clientSocket.getInputStream();

            // Set some SO flags.
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSoTimeout(READ_TIMEOUT * 1000); // This will automatically close persistent HTTP/1.1 requests.

            while (true) {
                HttpSession session = null;
                HttpVersion version = HttpVersion.HTTP_1_0; // Our default.
                // Note that we don't support 2.0 or 3.0, confirm this in RHSProtocol.

                // Catch any RHSHttpExceptions and convert them into responses.
                try {
                    session = RHSProtocol.accept(sessionLogger, this, clientSocket, in);
                    version = session.getVersion();
                    sessionLogger = session.getLogger();
                } catch (RHSHttpException e) {
                    sessionLogger.severe("An error occurred whilst handling a request:\n%s", e);
                    response = HttpResponse.newFixedLengthResponse(e.status);
                }

                sessionLogger.debug("Using version: %s", version);

                boolean keepConnectionAlive;
                switch (version) {
                    case HTTP_1_1:
                        if ("keep-alive".equalsIgnoreCase(session.getHeader("Connection"))) {
                            keepConnectionAlive = true;
                            break;
                        }

                    case HTTP_0_9:
                    case HTTP_1_0:
                    default:
                        keepConnectionAlive = false;
                        break;
                }

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
                boolean useChunkedResponse = false;

                // 0.9 doesn't have a status line or anything, so we don't write it out.
                if (version.value >= 1.0) {
                    sessionLogger.trace("Response status line: %s %s", version, response.getStatus().getStatusString());

                    // Write status.
                    writeString(version.toString(), out);
                    writeString(" ", out);
                    writeString(response.getStatus().getStatusString(), out);
                    writeString("\r\n", out);

                    if (keepConnectionAlive) {
                        // Add the keepalive headers.
                        response.putHeader("Connection", "keep-alive");
                        response.putHeader("Keep-Alive", "timeout=" + READ_TIMEOUT);
                    } else {
                        // Let the client know that we will be closing the socket.
                        response.putHeader("Connection", "close");
                    }

                    // Write out a Date header for HTTP/1.1 requests with a non-100 status code.
                    if ((version.value >= 1.1) && (response.getStatus().getStatusCode() >= 200)) {
                        response.putHeader("Date", getHttpTime());
                    }

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
                        useChunkedResponse = true;
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

                if (useChunkedResponse) {
                    out = new HttpChunkedOutputStream(out);
                }

                // Write out the response, defaulting to non-encoded responses.
                HttpServerUtil.writeWithEncoding(contentEncoding, out, response.getContent());

                if (useChunkedResponse) {
                    // Chunked output streams have special close implementations that don't actually
                    // close the connection.
                    out.close();
                }

                // Close the connection.
                if (!keepConnectionAlive) {
                    return;
                }

                // Reset the logger.
                sessionLogger = this.logger.createChild("<unknown (persistent) session> " + clientSocket.getPort());

                // Subsequent requests are handled by the while block.
                sessionLogger.debug("Keeping connection alive for subsequent requests.");
            }
        } catch (DropConnectionException ignored) {
            // Automatically handled by the finally {}.
            sessionLogger.debug("Dropping connection.");
        } catch (IOException e) {
            String message = e.getMessage();
            if ("Read timed out".equals(message)) {
                sessionLogger.debug("Persistent timed out, closing.");
                return;
            }

            sessionLogger.trace("An error occurred whilst handling a request:\n%s", e.toString());
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
                sessionLogger.severe("An error occurred whilst closing the socket:\n%s", e);
            }

            sessionLogger.trace("Closed the connection.");
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

    public static String getHttpTime() {
        return TIME_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    @AllArgsConstructor
    private static class HttpChunkedOutputStream extends OutputStream {
        private OutputStream out;

        @Override
        public void write(int b) throws IOException {
            this.out.write('1');
            writeString("\r\n", this.out);
            this.out.write(b);
            writeString("\r\n", this.out);
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (b.length == 0) return;

            writeString(Integer.toHexString(b.length), this.out);
            writeString("\r\n", this.out);
            this.out.write(b);
            writeString("\r\n", this.out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len == 0) return;

            writeString(Integer.toHexString(len), this.out);
            writeString("\r\n", this.out);
            this.out.write(b, off, len);
            writeString("\r\n", this.out);
        }

        @Override
        public void close() throws IOException {
            writeString("0\r\n\r\n", this.out);
            // Don't actually close the outputstream.
        }
    }

}
