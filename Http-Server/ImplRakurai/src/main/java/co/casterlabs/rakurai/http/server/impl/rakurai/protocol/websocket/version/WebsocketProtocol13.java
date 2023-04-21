package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket.version;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import co.casterlabs.rakurai.io.IOUtil;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WebsocketProtocol13 implements WebsocketProtocol {
    private final InputStream in;
    private final OutputStream out;

    @Override
    public ProtocolWebsocketFrame read() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public synchronized void sendFrame(boolean fin, int opcode, long payloadLen, byte[] data) throws IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public void close() throws IOException {
        this.sendFrame(true, 8, 0, new byte[0]);
        IOUtil.safeClose(this.in, this.out);
    }

}
