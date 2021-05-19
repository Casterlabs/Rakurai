package co.casterlabs.rakurai.io.http.websocket;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import lombok.NonNull;

public interface WebsocketSession {

    // Request headers
    public Map<String, String> getHeaders();

    default @Nullable String getHeader(@NonNull String header) {
        return this.getHeaders().get(header);
    }

    // URI
    public String getUri();

    public Map<String, List<String>> getAllQueryParameters();

    public Map<String, String> getQueryParameters();

    public String getQueryString();

    // Server info
    public String getHost();

    public int getPort();

    // Misc
    public String getRemoteIpAddress();

}
