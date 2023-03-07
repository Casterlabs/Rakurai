package co.casterlabs.rakurai.impl.http.rakurai;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpVersion;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class RHSHttpSession extends HttpSession {
    private @NonNull HeaderMap headers;

    private @NonNull String uri;
    private @NonNull String queryString;

    private int port;

    private @NonNull HttpVersion version;
    private @NonNull String method;
    private @NonNull String remoteAddress;

    // Request headers
    @Override
    public HeaderMap getHeaders() {
        return this.headers;
    }

    // URI
    @Override
    public String getUri() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> getQueryParameters() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getQueryString() {
        return this.queryString;
    }

    // Request body
    @Override
    public boolean hasBody() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @Nullable byte[] getRequestBodyBytes() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream getRequestBodyStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> parseFormBody() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    // Server info
    @Override
    public int getPort() {
        return this.port;
    }

    // Misc
    @Override
    public String getRawMethod() {
        return this.method;
    }

    @Override
    public HttpVersion getVersion() {
        return this.version;
    }

    @Override
    protected String getNetworkIpAddress() {
        return this.remoteAddress;
    }

}
