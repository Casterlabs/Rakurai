package co.casterlabs.rakurai.impl.http.nano;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import co.casterlabs.rakurai.impl.http.BinaryWebsocketFrame;
import co.casterlabs.rakurai.impl.http.TextWebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.Websocket;
import co.casterlabs.rakurai.io.http.websocket.WebsocketCloseCode;
import co.casterlabs.rakurai.io.http.websocket.WebsocketFrame;
import co.casterlabs.rakurai.io.http.websocket.WebsocketListener;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoWSD.WebSocket;
import fi.iki.elonen.NanoWSD.WebSocketFrame;
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode;
import fi.iki.elonen.NanoWSD.WebSocketFrame.OpCode;
import lombok.NonNull;

public class NanoWebsocketWrapper extends WebSocket {
    private WebsocketListener listener;

    private WebSocket instance = this;
    private RakuraiWebsocket rakWebsocket = new RakuraiWebsocket();

    public NanoWebsocketWrapper(IHTTPSession nanoSession, WebsocketListener listener) {
        super(nanoSession);

        this.listener = listener;
    }

    // Nano WebSocket Impl
    @Override
    protected void onOpen() {
        new Thread(() -> {
            while (this.isOpen()) {
                try {
                    this.ping(":x-ping".getBytes());

                    TimeUnit.SECONDS.sleep(1);
                } catch (Exception ignored) {}
            }
        }).start();

        this.listener.onOpen(this.rakWebsocket);
    }

    @Override
    protected void onClose(CloseCode code, String reason, boolean remote) {
        if (remote) {
            this.listener.onClose(this.rakWebsocket);
        }
    }

    @Override
    protected void onMessage(WebSocketFrame frame) {
        WebsocketFrame rakFrame = null;

        if (frame.getOpCode() == OpCode.Binary) {
            rakFrame = new BinaryWebsocketFrame(frame.getBinaryPayload());
        } else if (frame.getOpCode() == OpCode.Text) {
            rakFrame = new TextWebsocketFrame(frame.getTextPayload());
        }

        this.listener.onFrame(this.rakWebsocket, rakFrame);
    }

    @Override
    protected void onPong(WebSocketFrame pong) {}

    @Override
    protected void onException(IOException ignored) {}

    private class RakuraiWebsocket extends Websocket {

        @Override
        public void send(@NonNull String message) throws IOException {
            instance.send(message);
        }

        @Override
        public void send(@NonNull byte[] bytes) throws IOException {
            instance.send(bytes);
        }

        @Override
        public void close(@NonNull WebsocketCloseCode code) throws IOException {
            try {
                instance.close(CloseCode.find(code.getCode()), "", false);
            } catch (Exception ignored) {}
        }

        // Request headers
        @Override
        public Map<String, String> getHeaders() {
            return instance.getHandshakeRequest().getHeaders();
        }

        // URI
        @Override
        public String getUri() {
            return instance.getHandshakeRequest().getUri();
        }

        @Override
        public Map<String, List<String>> getAllQueryParameters() {
            return instance.getHandshakeRequest().getParameters();
        }

        @SuppressWarnings("deprecation")
        @Override
        public Map<String, String> getQueryParameters() {
            return instance.getHandshakeRequest().getParms();
        }

        @Override
        public String getQueryString() {
            if (instance.getHandshakeRequest().getQueryParameterString() == null) {
                return "";
            } else {
                return "?" + instance.getHandshakeRequest().getQueryParameterString();
            }
        }

        // Misc
        @Override
        public String getRemoteIpAddress() {
            return instance.getHandshakeRequest().getRemoteIpAddress();
        }

    }

}
