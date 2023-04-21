package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSHttpException;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSProtocol;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.DropConnectionException;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpResponse;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpSession;
import co.casterlabs.rakurai.io.http.server.config.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.config.HttpServerImplementation;
import lombok.Getter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@Getter
public class RakuraiHttpServer implements HttpServer {
    public static final int READ_TIMEOUT = 10;

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
            Socket clientSocket = this.serverSocket.accept();
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
            BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());

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

                    sessionLogger.debug("Request headers: %s", session.getHeaders());
                } catch (RHSHttpException e) {
                    sessionLogger.severe("An error occurred whilst handling a request:\n%s", e);
                    response = HttpResponse.newFixedLengthResponse(e.status);
                }

                sessionLogger.debug("Using version: %s", version);

                boolean keepConnectionAlive = false;
                String protocol = "http";

                switch (version) {
                    case HTTP_1_1: {
                        String connection = session.getHeader("Connection");

                        if ("upgrade".equalsIgnoreCase(connection)) {
                            String upgradeTo = session.getHeader("Upgrade");

                            if ("websocket".equalsIgnoreCase(upgradeTo)) {
                                protocol = "websocket";
                            }
                        } else if ("keep-alive".equalsIgnoreCase(connection)) {
                            keepConnectionAlive = true;
                        }
                        break;
                    }

                    case HTTP_0_9:
                    case HTTP_1_0:
                    default:
                        break;
                }

                switch (protocol) {
                    case "http": {
                        // We have a valid session, try to serve it.
                        // Note that response will always be null at this location IF session isn't.
                        if (session != null) {
                            response = this.listener.serveSession(session.getHost(), session, this.isSecure);
                        }

                        if (response == null) throw new DropConnectionException();

                        RHSProtocol.writeOutResponse(this, clientSocket, session, keepConnectionAlive, response);

                        // Close the connection.
                        if (!keepConnectionAlive) {
                            return;
                        }

                        // We're keeping the connection, reset the logger and let the while{} block
                        // handle subsequent requests.
                        sessionLogger = this.logger.createChild("<unknown (persistent) session> " + clientSocket.getPort());
                        sessionLogger.debug("Keeping connection alive for subsequent requests.");
                        break;
                    }

                    case "websocket": {
                        throw new DropConnectionException(); // TODO
                    }
                }
            }
        } catch (DropConnectionException ignored) {
            // Automatically handled by the finally {}.
            sessionLogger.debug("Dropping connection.");
        } catch (IOException e) {
            String message = e.getMessage();
            if ("Read timed out".equals(message)) {
                sessionLogger.debug("Read timed out, closing.");
                return;
            }

            sessionLogger.trace("An error occurred whilst handling a request:\n%s", e);
        } catch (IllegalStateException e) {} finally {
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

            new ArrayList<>(this.connectedClients)
                .forEach(IOUtil::safeClose);
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

}
