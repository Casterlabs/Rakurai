package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.version;

import java.io.IOException;

import co.casterlabs.rakurai.io.http.server.websocket.WebsocketCloseCode;
import lombok.NonNull;

public interface WebsocketProtocol {

    /**
     * @implNote this method is only ever called from the read thread.
     */
    public ProtocolWebsocketFrame read() throws IOException;

    /**
     * @implSpec this method MUST be synchronized.
     */
    public void sendFrame(boolean fin, int opcode, long payloadLen, byte[] data) throws IOException;

    public void close(@NonNull WebsocketCloseCode code) throws IOException;

}
