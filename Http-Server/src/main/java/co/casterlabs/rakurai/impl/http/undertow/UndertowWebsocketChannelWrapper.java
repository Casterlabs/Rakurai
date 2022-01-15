package co.casterlabs.rakurai.impl.http.undertow;

import java.io.IOException;
import java.nio.ByteBuffer;

import co.casterlabs.rakurai.io.http.websocket.Websocket;
import co.casterlabs.rakurai.io.http.websocket.WebsocketCloseCode;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import lombok.NonNull;

public class UndertowWebsocketChannelWrapper extends Websocket {
    private WebSocketChannel channel;

    public UndertowWebsocketChannelWrapper(WebSocketChannel channel, WebsocketSession session) {
        super(session);
        this.channel = channel;
    }

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

}
