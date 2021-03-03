package co.casterlabs.rakurai.io.http.websocket;

public interface WebsocketFrame {

    public WebsocketFrameType getFrameType();

    public String getAsText();

    public byte[] getBytes();

    public int getSize();

}
