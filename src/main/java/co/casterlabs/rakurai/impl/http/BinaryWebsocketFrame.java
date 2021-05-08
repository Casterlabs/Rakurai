package co.casterlabs.rakurai.impl.http;

import java.nio.charset.StandardCharsets;

import co.casterlabs.rakurai.io.http.websocket.WebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.WebsocketFrameType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BinaryWebsocketFrame extends WebsocketFrame {
    private byte[] bytes;

    @Override
    public WebsocketFrameType getFrameType() {
        return WebsocketFrameType.BINARY;
    }

    @Override
    public String getAsText() {
        return new String(this.bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getBytes() {
        return this.bytes;
    }

    @Override
    public int getSize() {
        return this.bytes.length;
    }

}
