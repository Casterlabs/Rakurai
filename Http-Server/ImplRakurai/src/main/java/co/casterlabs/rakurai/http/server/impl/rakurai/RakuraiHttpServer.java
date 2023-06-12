package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

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
    public static final int HTTP_PERSISTENT_TIMEOUT = 30;

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

            String remoteAddress = formatAddress(clientSocket);
            this.logger.debug("New connection from %s", remoteAddress);

            this.executor.execute(() -> {
                FastLogger sessionLogger = this.logger.createChild("Connection: " + remoteAddress);
                sessionLogger.debug("Handling request...");

                try {
                    clientSocket.setTcpNoDelay(true);

                    while (true) {
                        clientSocket.setSoTimeout(HTTP_PERSISTENT_TIMEOUT * 1000); // 1m timeout for regular requests.

                        boolean acceptAnotherRequest = this.handle(clientSocket, sessionLogger);
                        if (acceptAnotherRequest) {
                            // We're keeping the connection, let the while{} block do it's thing.
                            sessionLogger.debug("Keeping connection alive for subsequent requests.");
                        } else {
                            // Break out of this torment.
                            break;
                        }
                    }
                } catch (DropConnectionException ignored) {
                    sessionLogger.debug("Dropping connection.");
                } catch (Throwable e) {
                    if (!shouldIgnoreThrowable(e)) {
                        sessionLogger.fatal("An error occurred whilst handling request:\n%s", e);
                    }
                } finally {
                    Thread.interrupted(); // Clear interrupt status.

                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        sessionLogger.severe("An error occurred whilst closing the socket:\n%s", e);
                    }

                    this.connectedClients.remove(clientSocket);
                    this.logger.debug("Closed connection from %s", remoteAddress);
                }
            });
        } catch (IOException e) {
            this.logger.severe("An error occurred whilst accepting a new connection:\n%s", e);
        }
    }

    private boolean handle(Socket clientSocket, FastLogger sessionLogger) throws IOException, NoSuchAlgorithmException {
        HttpResponse httpResponse = null;
        WebsocketListener websocketListener = null;
        RHSWebsocket websocket = null;

        try {
            BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());

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
                                return false;
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

            switch (protocol) {
                case "http": {
                    // We have a valid session, try to serve it.
                    // Note that response will always be null at this location IF session isn't.
                    if (session != null) {
                        httpResponse = this.listener.serveSession(session.getHost(), session, this.isSecure);
                    }

                    if (httpResponse == null) throw new DropConnectionException();

                    RHSProtocol.writeOutResponse(clientSocket, session, keepConnectionAlive, httpResponse);

                    return keepConnectionAlive;
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
                                return false;
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

                    final Thread readThread = Thread.currentThread();
                    final RHSWebsocket $websocket_pointer = websocket;

                    Thread kaThread = new Thread(() -> {
                        try {
                            while (!clientSocket.isClosed()) {
                                RHSWebsocketProtocol.doPing($websocket_pointer);
                                Thread.sleep(RHSWebsocketProtocol.READ_TIMEOUT);
                            }
                        } catch (Exception ignored) {
                            readThread.interrupt(); // Try to tell the read thread that the connection is ded.
                        }
                    });
                    kaThread.setName("RHS Keep Alive Thread - " + session.getRequestId());
                    kaThread.setDaemon(true);
                    kaThread.start();

                    RHSWebsocketProtocol.handleWebsocketRequest(clientSocket, session, websocket, websocketListener);

                    return false; // Close.
                }

                default:
                    return false;
            }
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
        }
    }

    private static String formatAddress(Socket clientSocket) {
        String address = //
            ((InetSocketAddress) clientSocket.getRemoteSocketAddress())
                .getAddress()
                .toString()
                .replace("/", "");

        if (address.indexOf(':') != -1) {
            // Better Format for ipv6 addresses :^)
            address = '[' + address + ']';
        }

        address += ':';
        address += clientSocket.getPort();

        return address;
    }

    private static boolean shouldIgnoreThrowable(Throwable t) {
        if (t instanceof InterruptedException) return true;
        if (t instanceof SSLHandshakeException) return true;

        String message = t.getMessage();
        if (message == null) return false;
        message = message.toLowerCase();

        if (message.contains("socket closed") ||
            message.contains("socket is closed") ||
            message.contains("read timed out") ||
            message.contains("connection or inbound has closed") ||
            message.contains("connection reset") ||
            message.contains("received fatal alert: internal_error") ||
            message.contains("socket write error")) return true;

        return false;
    }

    @Override
    public void start() throws IOException {
        if (this.isAlive()) return;

        try {
            if (this.config.getSsl() == null) {
                this.serverSocket = new ServerSocket();
            } else {
                SSLContext sslContext = SSLContext.getInstance("TLS");

                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream(this.config.getSsl().getKeystoreLocation())) {
                    keyStore.load(fis, this.config.getSsl().getKeystorePassword());
                }

                KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManager.init(keyStore, this.config.getSsl().getKeystorePassword());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);

                sslContext.init(keyManager.getKeyManagers(), tmf.getTrustManagers(), null);

                SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
                SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket();

                List<String> cipherSuitesToUse;

                if (this.config.getSsl().getEnabledCipherSuites() == null) {
                    cipherSuitesToUse = Arrays.asList(factory.getSupportedCipherSuites());
                } else {
                    List<String> enabledCipherSuites = this.config.getSsl().getEnabledCipherSuites();

                    // Go through the list and make sure that the JVM supports the suite.
                    List<String> supported = new LinkedList<>();
                    for (String suite : factory.getSupportedCipherSuites()) {
                        if (enabledCipherSuites.contains(suite)) {
                            supported.add(suite);
                        } else {
                            this.logger.debug("Disabled Cipher Suite: %s.", suite);
                        }
                    }

                    for (String suite : enabledCipherSuites) {
                        if (!supported.contains(suite)) {
                            this.logger.warn("Unsupported Cipher Suite: %s.", suite);
                        }
                    }

                    cipherSuitesToUse = supported;
                }

                // If the certificate doesn't support EC algs, then we disable them.
                {
                    boolean ECsupported = false;

                    for (String alias : Collections.list(keyStore.aliases())) {
                        Certificate certificate = keyStore.getCertificate(alias);
                        if (certificate instanceof X509Certificate) {
                            X509Certificate x509Certificate = (X509Certificate) certificate;
                            String publicKeyAlgorithm = x509Certificate.getPublicKey().getAlgorithm();
                            if (publicKeyAlgorithm.equals("EC")) {
                                ECsupported = true;
                                break;
                            }
                        }
                    }

                    if (!ECsupported) {
                        Iterator<String> it = cipherSuitesToUse.iterator();
                        boolean warnedECunsupported = false;

                        while (it.hasNext()) {
                            String cipherSuite = it.next();
                            if (cipherSuite.contains("_ECDHE_") || cipherSuite.contains("_ECDH_")) {
                                it.remove();

                                if (!warnedECunsupported) {
                                    warnedECunsupported = true;
                                    this.logger.warn("Elliptic-Curve Cipher Suites are not supported as your certificate does not use the EC public key algorithm.");
                                }
                            }
                        }
                    }
                }

                this.logger.info("Using the following Cipher Suites: %s.", cipherSuitesToUse);
                socket.setEnabledCipherSuites(cipherSuitesToUse.toArray(new String[0]));

                socket.setEnabledProtocols(this.config.getSsl().convertTLS());
                socket.setUseClientMode(false);
                socket.setWantClientAuth(false);
                socket.setNeedClientAuth(false);

                this.serverSocket = socket;
            }

            this.serverSocket.setReuseAddress(true);
            this.serverSocket.bind(new InetSocketAddress(this.config.getHostname(), this.config.getPort()));
        } catch (Exception e) {
            this.serverSocket = null;
            throw new IOException("Unable to start server", e);
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
