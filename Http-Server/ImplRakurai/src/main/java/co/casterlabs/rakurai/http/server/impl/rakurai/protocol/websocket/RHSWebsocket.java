package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import co.casterlabs.rakurai.io.BigEndianIOUtil;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.server.websocket.Websocket;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketSession;
import lombok.NonNull;

public class RHSWebsocket extends Websocket {
    private final Closeable toClose;
    private final OutputStream out;

    public RHSWebsocket(@NonNull WebsocketSession session, @NonNull OutputStream out, @NonNull Closeable toClose) {
        super(session);
        this.out = out;
        this.toClose = toClose;
    }

    @Override
    public void send(@NonNull String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        int toWrite = bytes.length;
        int written = 0;

        while (toWrite > 0) {
            byte[] chunk = new byte[Math.min(toWrite, RHSWebsocketProtocol.MAX_CHUNK_LENGTH)];
            System.arraycopy(bytes, written, chunk, 0, chunk.length);
            toWrite -= chunk.length;

            boolean fin = toWrite == 0;
            int op = written == 0 ? 1 : 0; // Note the op code of 1.
            this.sendFrame(fin, op, chunk);

            written += chunk.length;
        }
    }

    @Override
    public void send(@NonNull byte[] bytes) throws IOException {
        int toWrite = bytes.length;
        int written = 0;

        while (toWrite > 0) {
            byte[] chunk = new byte[Math.min(toWrite, RHSWebsocketProtocol.MAX_CHUNK_LENGTH)];
            System.arraycopy(bytes, written, chunk, 0, chunk.length);
            toWrite -= chunk.length;

            boolean fin = toWrite == 0;
            int op = written == 0 ? 2 : 0; // Note the op code of 2.
            this.sendFrame(fin, op, chunk);

            written += chunk.length;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.sendFrame(true, 8, new byte[0]);
        } finally {
            IOUtil.safeClose(this.toClose);
        }
    }

    void sendFrame(boolean fin, int op, byte[] bytes) throws IOException {
        assert op < 16 && op >= 0 : "Opcode must be 0-15.";

        int len7 = bytes.length;
        if (len7 > 125) {
            if (bytes.length > Math.pow(2, 16)) {
                len7 = 127; // Use 64bit length.
            } else {
                len7 = 126; // Use 16bit length.
            }
        }

        {
            int headerByte = 0; // We leave RSV123 and MASK set to 0.
            headerByte |= len7;
            headerByte |= op << 8;
            headerByte |= (fin ? 1 : 0) << 15;

            byte[] headerBytes = BigEndianIOUtil.intToBytes(headerByte);
            this.out.write(headerBytes[2]);
            this.out.write(headerBytes[3]); // We only need the first 16 bits.
        }

        if (len7 == 126) {
            byte[] lenBytes = BigEndianIOUtil.intToBytes(bytes.length);
            this.out.write(lenBytes[2]);
            this.out.write(lenBytes[3]); // We only need the first 16 bits.
        } else if (len7 == 127) {
            byte[] lenBytes = BigEndianIOUtil.longToBytes(bytes.length);
            this.out.write(lenBytes); // We only need the first 16 bits.
        }

        this.out.write(bytes);
    }

}
