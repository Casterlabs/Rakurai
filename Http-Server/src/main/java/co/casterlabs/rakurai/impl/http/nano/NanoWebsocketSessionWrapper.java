package co.casterlabs.rakurai.impl.http.nano;

import java.util.List;
import java.util.Map;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

public class NanoWebsocketSessionWrapper extends WebsocketSession {
    private IHTTPSession nanoSession;
    private int port;

    private HeaderMap headers;

    public NanoWebsocketSessionWrapper(IHTTPSession nanoSession, int port, HttpServerBuilder config) {
        this.nanoSession = nanoSession;
        this.port = port;
        this.headers = new HeaderMap.Builder().putSingleMap(this.nanoSession.getHeaders()).build();

        super.postConstruct(config);
    }

    // Headers
    @Override
    public HeaderMap getHeaders() {
        return this.headers;
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
    public HttpVersion getVersion() {
        return HttpVersion.HTTP_1_1;
    }

    @Override
    protected String getNetworkIpAddress() {
        return this.nanoSession.getRemoteIpAddress();
    }

}
