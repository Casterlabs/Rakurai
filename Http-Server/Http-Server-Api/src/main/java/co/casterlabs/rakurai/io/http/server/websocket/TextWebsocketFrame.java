package co.casterlabs.rakurai.io.http.server.websocket;

import java.nio.charset.StandardCharsets;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TextWebsocketFrame extends WebsocketFrame {
    private String text;

    @Override
    public WebsocketFrameType getFrameType() {
        return WebsocketFrameType.TEXT;
    }

    @Override
    public String getAsText() {
        return this.text;
    }

    @Override
    public byte[] getBytes() {
        return this.text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int getSize() {
        return this.getBytes().length;
    }

}
