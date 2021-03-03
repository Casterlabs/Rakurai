package co.casterlabs.rakurai.impl.http.undertow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;

public class UndertowWebsocketSessionWrapper implements WebsocketSession {
    private WebSocketHttpExchange exchange;
    private WebSocketChannel channel;
    private int port;

    private Map<String, List<String>> allQueryParameters = new HashMap<>();
    private Map<String, String> queryParameters = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();

    public UndertowWebsocketSessionWrapper(WebSocketHttpExchange exchange, WebSocketChannel channel, int port) {
        this.exchange = exchange;
        this.channel = channel;
        this.port = port;

        Map<String, List<String>> headers = exchange.getRequestHeaders();

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            this.headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
        }

        for (Map.Entry<String, List<String>> entry : exchange.getRequestParameters().entrySet()) {
            this.allQueryParameters.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            this.queryParameters.put(entry.getKey(), entry.getValue().get(0));
        }

        this.headers = Collections.unmodifiableMap(this.headers);
    }

    // Request headers
    @Override
    public Map<String, String> getHeaders() {
        return this.headers;
    }

    // URI
    @Override
    public String getUri() {
        return this.exchange.getRequestURI().split("\\?")[0];
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        return this.allQueryParameters;
    }

    @Override
    public Map<String, String> getQueryParameters() {
        return this.queryParameters;
    }

    @Override
    public String getQueryString() {
        if (this.exchange.getQueryString() == null) {
            return "";
        } else {
            return "?" + this.exchange.getQueryString();
        }
    }

    // Server Info
    @Override
    public String getHost() {
        return this.getHeader("host");
    }

    @Override
    public int getPort() {
        return this.port;
    }

    // Misc
    @Override
    public String getRemoteIpAddress() {
        return this.channel.getSourceAddress().getHostString();
    }

}
