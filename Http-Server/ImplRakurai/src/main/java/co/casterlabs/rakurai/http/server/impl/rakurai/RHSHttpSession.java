package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.HttpSession;
import co.casterlabs.rakurai.io.http.server.config.HttpServerBuilder;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@AllArgsConstructor
public class RHSHttpSession extends HttpSession {
    private @NonNull HeaderMap headers;

    private @NonNull String uri;
    private @NonNull String queryString;
    private @NonNull Map<String, List<String>> allQueryParameters;
    private @NonNull Map<String, String> queryParameters;

    private int port;

    private @NonNull HttpVersion version;
    private @NonNull String method;
    private @NonNull String remoteAddress;

    private @Nullable InputStream bodyIn;

    RHSHttpSession rhsPostConstruct(HttpServerBuilder config, FastLogger parentLogger) {
        super.postConstruct(config, parentLogger);
        return this;
    }

    // Request headers
    @Override
    public HeaderMap getHeaders() {
        return this.headers;
    }

    // URI
    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public String getQueryString() {
        return this.queryString;
    }

    @Override
    public Map<String, String> getQueryParameters() {
        return this.queryParameters;
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        return this.allQueryParameters;
    }

    // Request body
    @Override
    public boolean hasBody() {
        return this.bodyIn != null;
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
