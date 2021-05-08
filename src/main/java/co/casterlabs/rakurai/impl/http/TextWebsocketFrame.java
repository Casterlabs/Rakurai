package co.casterlabs.rakurai.impl.http;

import co.casterlabs.rakurai.io.http.websocket.WebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.WebsocketFrameType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TextWebsocketFrame implements WebsocketFrame {
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
        return this.text.getBytes();
    }

    @Override
    public int getSize() {
        return this.text.length();
    }

}
