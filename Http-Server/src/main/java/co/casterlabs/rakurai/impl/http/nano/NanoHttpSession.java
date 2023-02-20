package co.casterlabs.rakurai.impl.http.nano;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.ResponseException;
import lombok.Getter;

public class NanoHttpSession extends HttpSession {
    private @Getter IHTTPSession nanoSession;
    private int port;

    private HeaderMap headers;
    private byte[] body;

    public NanoHttpSession(IHTTPSession nanoSession, int port, HttpServerBuilder config) {
        this.port = port;
        this.nanoSession = nanoSession;
        this.headers = new HeaderMap.Builder().putSingleMap(this.nanoSession.getHeaders()).build();

        this.postConstruct(config);
    }

    // Request headers
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

    // Request body
    @Override
    public boolean hasBody() {
        return this.getHeader("content-length") != null;
    }

    @Override
    public @Nullable byte[] getRequestBodyBytes() throws IOException {
        if (this.body == null) {
            if (this.hasBody()) {
                this.body = IOUtil.readInputStreamBytes(this.nanoSession.getInputStream());

                return this.body;
            } else {
                return this.body = new byte[0];
            }
        } else {
            return this.body;
        }
    }

    @Override
    public Map<String, String> parseFormBody() throws IOException {
        try {
            Map<String, String> files = new HashMap<>();

            this.nanoSession.parseBody(files);

            return files;
        } catch (ResponseException e) {
            throw new IOException(e);
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
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(this.nanoSession.getMethod().name());
    }

    @Override
    public HttpVersion getVersion() {
        return HttpVersion.HTTP_1_1;
    }

    @Override
    protected String getNetworkIpAddress() {
        return this.nanoSession.getRemoteIpAddress();
    }

}
