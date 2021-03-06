package co.casterlabs.rakurai.impl.http.undertow;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import co.casterlabs.rakurai.io.http.websocket.Websocket;
import co.casterlabs.rakurai.io.http.websocket.WebsocketCloseCode;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class UndertowWebsocketChannelWrapper extends Websocket {
    private WebSocketChannel channel;
    private WebsocketSession session;

    @Override
    public void send(@NonNull String message) throws IOException {
        WebSockets.sendText(message, this.channel, null);
    }

    @Override
    public void send(@NonNull byte[] bytes) throws IOException {
        WebSockets.sendBinary(ByteBuffer.wrap(bytes), this.channel, null);
    }

    @Override
    public void close(@NonNull WebsocketCloseCode code) throws IOException {
        try {
            this.channel.sendClose();
        } catch (IOException e) {}
    }

    @Override
    public Map<String, String> getHeaders() {
        return this.session.getHeaders();
    }

    @Override
    public String getUri() {
        return this.session.getUri();
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        return this.session.getAllQueryParameters();
    }

    @Override
    public Map<String, String> getQueryParameters() {
        return this.session.getQueryParameters();
    }

    @Override
    public String getQueryString() {
        return this.session.getQueryString();
    }

    @Override
    public String getRemoteIpAddress() {
        return this.session.getRemoteIpAddress();
    }

}
