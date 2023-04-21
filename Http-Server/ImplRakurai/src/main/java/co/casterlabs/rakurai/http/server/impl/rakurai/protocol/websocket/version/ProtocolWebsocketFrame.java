package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.version;

public class ProtocolWebsocketFrame {
    public boolean fin;
    public byte opcode;
    public boolean maskPresent;
    public int payloadLength;
    public byte[] payload;

}
