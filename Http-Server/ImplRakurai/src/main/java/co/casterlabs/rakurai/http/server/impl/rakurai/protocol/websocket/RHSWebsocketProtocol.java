package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket;

import java.io.IOException;
import java.net.Socket;

import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSHttpSession;
import co.casterlabs.rakurai.io.BigEndianIOUtil;
import co.casterlabs.rakurai.io.http.server.websocket.Websocket;
import co.casterlabs.rakurai.io.http.server.websocket.WebsocketListener;

public class RHSWebsocketProtocol {
    public static final int MAX_CHUNK_LENGTH = 16 * 1024; // 16kb

    public static void doPing(RHSWebsocket websocket) {
        byte[] someBytes = BigEndianIOUtil.longToBytes(System.currentTimeMillis());
        try {
            websocket.sendFrame(true, 9, someBytes);
        } catch (IOException ignored) {}
    }

    public static void handleWebsocketRequest(Socket client, RHSHttpSession session, Websocket websocket, WebsocketListener websocketListener) {
        // TODO frame read and decode logic.

    }

}
