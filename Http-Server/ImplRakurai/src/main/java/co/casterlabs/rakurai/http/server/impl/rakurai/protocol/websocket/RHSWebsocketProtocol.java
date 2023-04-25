package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import co.casterlabs.rakurai.DataSize;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSHttpSession;
import co.casterlabs.rakurai.io.BigEndianIOUtil;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketListener;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RHSWebsocketProtocol {
    public static final int MAX_CHUNK_LENGTH = (int) DataSize.KILOBYTE.toBytes(16);
    public static final int MAX_PAYLOAD_LENGTH = (int) DataSize.MEGABYTE.toBytes(16);
    public static final long READ_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    public static void doPing(RHSWebsocket websocket) {
        byte[] someBytes = BigEndianIOUtil.longToBytes(System.currentTimeMillis());
        try {
            websocket.sendFrame(true, WebsocketOpCode.PING, someBytes);
        } catch (IOException ignored) {}
    }

    public static void handleWebsocketRequest(Socket client, RHSHttpSession session, RHSWebsocket websocket, WebsocketListener listener) throws IOException {
        InputStream in = client.getInputStream();
        FastLogger sessionLogger = session.getLogger();

        // For continuation frames.
        int fragmentedOpCode = 0;
        byte[] fragmentedPacket = new byte[0];

        while (!client.isClosed()) {
            // @formatter:off
            int header1 = in.read();
            boolean isFinished = (header1 & 0b10000000) != 0;
            boolean rsv1       = (header1 & 0b01000000) != 0;
            boolean rsv2       = (header1 & 0b00100000) != 0;
            boolean rsv3       = (header1 & 0b00010000) != 0;
            int op             =  header1 & 0b00001111;
            
            int header2 = in.read();
            boolean isMasked   = (header2 & 0b10000000) != 0;
            int len7           =  header2 & 0b01111111;
            // @formatter:on

            if (rsv1 || rsv2 || rsv3) {
                session.getLogger().fatal("Reserved bits are set, these are not supported! rsv1=%b rsv2=%b rsv3=%b", rsv1, rsv2, rsv3);
                return;
            }

            sessionLogger.trace("fin=%b op=%d mask=%b len7=%d", isFinished, op, isMasked, len7);

            long length;

            if (len7 == 126) {
                // 16bit.
                length = BigEndianIOUtil.bytesToInt(new byte[] {
                        0,
                        0,
                        (byte) in.read(),
                        (byte) in.read(),
                });
            } else if (len7 == 127) {
                // 64bit.
                length = BigEndianIOUtil.bytesToLong(new byte[] {
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                });

                if (Long.compareUnsigned(length, MAX_PAYLOAD_LENGTH) > 0) {
                    sessionLogger.fatal(
                        "Payload length too big, max 16mb got %smb.",
                        DataSize.MEGABYTE.format(DataSize.MEGABYTE.fromBytes(length))
                    );
                    return;
                }
            } else {
                length = len7;
            }

            sessionLogger.trace("trueLength=%d", length);

            byte[] maskingKey = null;
            if (isMasked) {
                maskingKey = new byte[] {
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                        (byte) in.read(),
                };
            }

            // Read in the whole payload.
            byte[] payload = new byte[(int) length];

            int read = 0;
            while ((read += in.read(payload)) < payload.length);

            // XOR decrypt.
            if (isMasked) {
                for (int idx = 0; idx < payload.length; idx++) {
                    payload[idx] ^= maskingKey[idx % 4];
                }
            }

            // We're starting a new fragmented message, store this info for later.
            if (!isFinished && op != 0) {
                fragmentedOpCode = op;
            }

            // Handle fragmented messages.
            if (op == 0) {
                int totalLength = fragmentedPacket.length + payload.length;
                if (totalLength > MAX_PAYLOAD_LENGTH) {
                    sessionLogger.fatal(
                        "Fragmented payload length too big, max 16mb got %smb.",
                        DataSize.MEGABYTE.format(DataSize.MEGABYTE.fromBytes(length))
                    );
                    return;
                }

                byte[] wholePayload = new byte[totalLength];
                System.arraycopy(fragmentedPacket, 0, wholePayload, 0, fragmentedPacket.length);
                System.arraycopy(payload, 0, wholePayload, fragmentedPacket.length, payload.length);

                fragmentedPacket = wholePayload;

                if (!isFinished) {
                    // Client is not yet finished next packet pls.
                    continue;
                }

                // We're finished! Parse it!
                payload = fragmentedPacket;
                op = fragmentedOpCode;
                fragmentedPacket = new byte[0];
                break;
            }

            // Parse the op code and do behavior tingz.
            switch (op) {

                case 0x1: { // Text
                    try {
                        String text = new String(payload, StandardCharsets.UTF_8);
                        sessionLogger.debug("Text frame: %s", text);
                        listener.onText(websocket, text);
                    } catch (Throwable t) {
                        sessionLogger.severe("Listener produced exception:\n%s", t);
                    }
                    break;
                }

                case 0x2: { // Binary
                    try {
                        sessionLogger.debug("Binary frame: len=%d", payload.length);
                        listener.onBinary(websocket, payload);
                    } catch (Throwable t) {
                        sessionLogger.severe("Listener produced exception:\n%s", t);
                    }
                    break;
                }

                case 0x8: { // Close
                    websocket.close(); // Send close reply.
                    return;
                }

                case 0x9: { // Ping
                    websocket.sendFrame(true, WebsocketOpCode.PONG, payload); // Send pong reply.
                    continue;
                }

                case 0xa: { // Pong
                    continue;
                }

                default: // Reserved
                    continue;

            }
        }
    }

}