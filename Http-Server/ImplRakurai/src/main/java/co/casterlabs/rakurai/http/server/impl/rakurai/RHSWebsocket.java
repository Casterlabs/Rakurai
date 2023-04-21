package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.RHSWebsocketProtocol;
import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.version.WebsocketProtocol;
import co.casterlabs.rakurai.io.http.server.websocket.Websocket;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketCloseCode;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketSession;
import lombok.NonNull;

public class RHSWebsocket extends Websocket {
    private final WebsocketProtocol protocol;

    public RHSWebsocket(@NonNull WebsocketSession session, @NonNull WebsocketProtocol protocol) {
        super(session);
        this.protocol = protocol;
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
            this.protocol.sendFrame(fin, op, bytes.length, bytes);

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
            this.protocol.sendFrame(fin, op, bytes.length, bytes);

            written += chunk.length;
        }
    }

    @Override
    public void close(@NonNull WebsocketCloseCode code) throws IOException {
        this.protocol.close(code);
    }

}
