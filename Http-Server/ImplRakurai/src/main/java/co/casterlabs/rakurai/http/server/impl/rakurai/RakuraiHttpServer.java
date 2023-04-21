package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSHttpException;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSHttpSession;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSProtocol;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.RHSWebsocket;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.RHSWebsocketProtocol;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.DropConnectionException;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpResponse;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.config.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.config.HttpServerImplementation;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketListener;
import lombok.Getter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@Getter
public class RakuraiHttpServer implements HttpServer {
    public static final int READ_TIMEOUT = 10;

    private static final byte[] HTTP_1_1_UPGRADE_REJECT = "HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(RHSProtocol.HEADER_CHARSET);

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

        HttpResponse httpResponse = null;
        WebsocketListener websocketListener = null;
        RHSWebsocket websocket = null;

        try {
            BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());

            clientSocket.setTcpNoDelay(true);
            clientSocket.setSoTimeout(60 * 1000); // 1m timeout for initial request.

            while (true) {
                httpResponse = null;
                websocketListener = null;
                websocket = null;

                RHSHttpSession session = null;
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
                    httpResponse = HttpResponse.newFixedLengthResponse(e.status);
                }

                sessionLogger.debug("Using version: %s", version);

                boolean keepConnectionAlive = false;
                String protocol = "http";

                switch (version) {
                    case HTTP_1_1: {
                        String connection = session.getHeader("Connection");

                        if ("upgrade".equalsIgnoreCase(connection)) {
                            String upgradeTo = session.getHeader("Upgrade");
                            if (upgradeTo == null) upgradeTo = "";

                            switch (upgradeTo.toLowerCase()) {
                                case "websocket": {
                                    protocol = "websocket";
                                    break;
                                }

                                default: {
                                    clientSocket.getOutputStream().write(HTTP_1_1_UPGRADE_REJECT);
                                    return;
                                }
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

                clientSocket.setSoTimeout(0); // Disable the timeout while we handle the request.

                switch (protocol) {
                    case "http": {
                        // We have a valid session, try to serve it.
                        // Note that response will always be null at this location IF session isn't.
                        if (session != null) {
                            httpResponse = this.listener.serveSession(session.getHost(), session, this.isSecure);
                        }

                        if (httpResponse == null) throw new DropConnectionException();

                        RHSProtocol.writeOutResponse(clientSocket, session, keepConnectionAlive, httpResponse);

                        // Close the connection.
                        if (!keepConnectionAlive) {
                            return;
                        }

                        // We're keeping the connection, reset the logger and let the while{} block
                        // handle subsequent requests.
                        sessionLogger = this.logger.createChild("<unknown (persistent) session> " + clientSocket.getPort());
                        sessionLogger.debug("Keeping connection alive for subsequent requests.");
                        clientSocket.setSoTimeout(READ_TIMEOUT * 1000); // This will automatically close persistent HTTP/1.1 requests.
                        break;
                    }

                    case "websocket": {
                        sessionLogger.debug("Handling websocket request...");

                        if (session != null) {
                            websocketListener = this.listener.serveWebsocketSession(session.getHost(), session, this.isSecure);
                        }

                        if (websocketListener == null) throw new DropConnectionException();

                        OutputStream out = clientSocket.getOutputStream();

                        {
                            String wsVersion = session.getHeader("Sec-WebSocket-Version");
                            if (wsVersion == null) wsVersion = "";

                            switch (wsVersion) {
                                // Supported.
                                case "13":
                                    break;

                                // Not supported.
                                default: {
                                    sessionLogger.debug("Rejected websocket version: %s", wsVersion);
                                    RHSProtocol.writeString("HTTP/1.1 426 Upgrade Required\r\n", out);
                                    RHSProtocol.writeString("Sec-WebSocket-Version: 13\r\n", out);
                                    RHSProtocol.writeString("\r\n", out);
                                    return;
                                }
                            }

                            sessionLogger.debug("Accepted websocket version: %s", wsVersion);
                        }

                        // Upgrade the connection.
                        RHSProtocol.writeString("HTTP/1.1 101 Switching Protocols\r\n", out);
                        sessionLogger.trace("Response status line: HTTP/1.1 101 Switching Protocols");

                        RHSProtocol.writeString("Connection: Upgrade\r\n", out);
                        RHSProtocol.writeString("Upgrade: websocket\r\n", out);

                        // Generate the key and send it out.
                        {
                            String clientKey = session.getHeader("Sec-WebSocket-Key");

                            if (clientKey != null) {
                                MessageDigest hash = MessageDigest.getInstance("SHA-1");
                                hash.reset();
                                hash.update(
                                    clientKey
                                        .concat("258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                        .getBytes(StandardCharsets.UTF_8)
                                );

                                String acceptKey = Base64.getEncoder().encodeToString(hash.digest());
                                RHSProtocol.writeString("Sec-WebSocket-Accept: ", out);
                                RHSProtocol.writeString(acceptKey, out);
                                RHSProtocol.writeString("\r\n", out);
                            }
                        }

                        {
                            // Select the first WS protocol, if any are requested.
                            String wsProtocol = session.getHeader("Sec-WebSocket-Protocol");
                            if (wsProtocol != null) {
                                String first = wsProtocol.split(",")[0].trim();

                                RHSProtocol.writeString("Sec-WebSocket-Protocol: ", out);
                                RHSProtocol.writeString(first, out);
                                RHSProtocol.writeString("\r\n", out);
                            }
                        }

                        // Write the separation line.
                        RHSProtocol.writeString("\r\n", out);

                        sessionLogger.debug("WebSocket upgrade complete, handling frames.");

                        websocket = new RHSWebsocket(session, out, clientSocket);
                        websocketListener.onOpen(websocket);

                        // Ping/pong mechanism.
                        clientSocket.setSoTimeout((int) (RHSWebsocketProtocol.READ_TIMEOUT * 4)); // Timeouts should work differently for WS.

                        final RHSWebsocket $websocket_pointer = websocket;
                        Thread kaThread = new Thread(() -> {
                            while (!clientSocket.isClosed()) {
                                RHSWebsocketProtocol.doPing($websocket_pointer);
                                try {
                                    Thread.sleep(RHSWebsocketProtocol.READ_TIMEOUT);
                                } catch (InterruptedException ignored) {}
                            }
                        });
                        kaThread.setName("RHS Keep Alive Thread - " + session.getRequestId());
                        kaThread.setDaemon(true);
                        kaThread.start();

                        RHSWebsocketProtocol.handleWebsocketRequest(clientSocket, session, websocket, websocketListener);
                        Thread.sleep(15000);
                        return; // Close the connection when we're done.
                    }
                }
            }

        } catch (DropConnectionException ignored) {
            sessionLogger.debug("Dropping connection.");
        } catch (IOException e) {
            String message = e.getMessage();
            if ("Read timed out".equals(message)) {
                sessionLogger.debug("Read timed out, closing.");
                return;
            }

            sessionLogger.trace("An error occurred whilst handling a request:\n%s", e);
        } catch (Exception e) {
            sessionLogger.fatal("A fatal error occurred whilst handling a request:\n%s", e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.getContent().close();
                } catch (Exception e) {
                    sessionLogger.severe("An error occurred whilst response content:\n%s", e);
                }
            }

            if (websocketListener != null) {
                try {
                    websocketListener.onClose(websocket);
                } catch (Exception e) {
                    sessionLogger.severe("An error occurred whilst response listener:\n%s", e);
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
