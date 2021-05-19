package co.casterlabs.rakurai.impl.http.nano;

import java.util.List;
import java.util.Map;

import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class NanoWebsocketSessionWrapper implements WebsocketSession {
    private IHTTPSession nanoSession;
    private int port;

    // Headers
    @Override
    public Map<String, String> getHeaders() {
        return this.nanoSession.getHeaders();
    }

    // URI
    @Override
    public String getUri() {
        return this.nanoSession.getUri();
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        return this.nanoSession.getParameters();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Map<String, String> getQueryParameters() {
        return this.nanoSession.getParms();
    }

    @Override
    public String getQueryString() {
        if (this.nanoSession.getQueryParameterString() == null) {
            return "";
        } else {
            return "?" + this.nanoSession.getQueryParameterString();
        }
    }

    // Server info
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
        return this.nanoSession.getRemoteIpAddress();
    }

}
