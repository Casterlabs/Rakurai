package co.casterlabs.rakurai.io.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import lombok.NonNull;

public abstract class HttpSession {

    // Request headers
    public abstract Map<String, String> getHeaders();

    public final @Nullable String getHeader(@NonNull String header) {
        return this.getHeaders().get(header.toLowerCase());
    }

    // URI
    public abstract String getUri();

    public abstract Map<String, List<String>> getAllQueryParameters();

    public abstract Map<String, String> getQueryParameters();

    public abstract String getQueryString();

    // Request body
    public abstract boolean hasBody();

    public final @Nullable String getRequestBody() throws IOException {
        if (this.hasBody()) {
            return new String(this.getRequestBodyBytes(), StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    public abstract @Nullable byte[] getRequestBodyBytes() throws IOException;

    public abstract Map<String, String> parseFormBody() throws IOException;

    // Server info
    public abstract String getHost();

    public abstract int getPort();

    // Misc
    public abstract HttpMethod getMethod();

    public abstract HttpVersion getVersion();

    public abstract String getRemoteIpAddress();

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("HttpSession(");

        sb.append("\n    method=").append(this.getMethod());
        sb.append("\n    version=").append(this.getVersion());
        sb.append("\n    port=").append(this.getPort());
        sb.append("\n    host=").append(this.getHost());
        sb.append("\n    remoteIpAddress=").append(this.getRemoteIpAddress());

        sb.append("\n    uri=").append(this.getUri()).append(this.getQueryString());

        sb.append("\n)");

        return sb.toString();
    }

}
