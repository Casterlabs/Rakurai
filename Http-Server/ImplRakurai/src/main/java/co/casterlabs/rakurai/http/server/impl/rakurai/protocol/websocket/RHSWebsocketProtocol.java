package co.casterlabs.rakurai.http.server.impl.rakurai.protocol.websocket;

import java.net.Socket;

import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpSession;

public class RHSWebsocketProtocol {
    public static final int MAX_CHUNK_LENGTH = 16 * 1024; // 16kb

    public static void handleWebsocketRequest(Socket client, HttpSession session, HttpListener listener) {

    }

}
