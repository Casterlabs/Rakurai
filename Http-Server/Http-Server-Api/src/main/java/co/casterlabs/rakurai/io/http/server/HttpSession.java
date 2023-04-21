package co.casterlabs.rakurai.io.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.HttpVersion;
import co.casterlabs.rakurai.io.http.server.config.HttpServerBuilder;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import lombok.Getter;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.LogUtil;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public abstract class HttpSession {
    private final @Getter String requestId = UUID.randomUUID().toString();

    ByteArrayOutputStream printResult;
    PrintStream printOutput;
    boolean hasSessionErrored = false;

    private @Getter FastLogger logger;

    private @Getter boolean isProxied;
    private String remoteIp;

    private byte[] cachedBody;

    protected void postConstruct(HttpServerBuilder config, FastLogger parentLogger) {
        this.isProxied = config.isBehindProxy();

        FastLogger realLogger = parentLogger.createChild("Session: " + this.requestId);

        if (config.getLogsDir() != null) {
            this.printResult = new ByteArrayOutputStream();
            this.printOutput = new PrintStream(this.printResult);

            this.printOutput.println("---- Start of log ----");
        }

        this.remoteIp = this.getRequestHops().get(0);

        this.logger = new FastLogger(this.requestId) {
            @Override
            public FastLogger log(@NonNull LogLevel level, @Nullable Object object, @Nullable Object... args) {
                if (level.canLog(LogLevel.WARNING)) {
                    hasSessionErrored = true;
                }

                realLogger.log(level, object, args);

                if (printOutput != null) {
                    String line = LogUtil.parseFormat(object, args);
                    printOutput.println(line);
                }

                return this;
            }
        };
    }

    // Request headers
    public abstract HeaderMap getHeaders();

    public final @Nullable String getHeader(@NonNull String header) {
        return this.getHeaders().getSingle(header);
    }

    // URI
    public abstract String getUri();

    public abstract Map<String, List<String>> getAllQueryParameters();

    public abstract Map<String, String> getQueryParameters();

    public abstract String getQueryString();

    // Request body
    public final @Nullable String getBodyMimeType() {
        return this.getHeader("Content-Type");
    }

    public abstract boolean hasBody();

    public final @Nullable String getRequestBody() throws IOException {
        if (this.hasBody()) {
            return new String(this.getRequestBodyBytes(), StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    public final @NonNull JsonElement getRequestBodyJson(@Nullable Rson rson) throws IOException, JsonParseException {
        if (this.hasBody()) {
            if (rson == null) {
                rson = Rson.DEFAULT;
            }

            if ("application/json".equals(this.getBodyMimeType())) {
                String body = new String(this.getRequestBodyBytes(), StandardCharsets.UTF_8);

                switch (body.charAt(0)) {
                    case '{': {
                        return rson.fromJson(body, JsonObject.class);
                    }

                    case '[': {
                        return rson.fromJson(body, JsonArray.class);
                    }

                    default: {
                        throw new JsonParseException("Request body must be either a JsonObject or JsonArray.");
                    }
                }
            } else {
                throw new JsonParseException("Request body must have a Content-Type of application/json.");
            }
        } else {
            return null;
        }
    }

    public @Nullable byte[] getRequestBodyBytes() throws IOException {
        if (!this.hasBody()) return null;

        if (this.cachedBody == null) {
            this.cachedBody = IOUtil.readInputStreamBytes(this.getRequestBodyStream());
        }

        return this.cachedBody;
    }

    public abstract @Nullable InputStream getRequestBodyStream() throws IOException;

    public abstract Map<String, String> parseFormBody() throws IOException;

    // Server info
    /**
     * @return the value from the HOST header.
     */
    public final @Nullable String getHost() {
        double version = this.getVersion().value;

        if (version < 1) {
            return null;
        } else if (version >= 2) {
            return this.getHeader(":authority");
        } else {
            return this.getHeader("Host");
        }
    }

    public abstract int getPort();

    // Misc
    public final HttpMethod getMethod() {
        return HttpMethod.from(this.getRawMethod());
    }

    public abstract String getRawMethod();

    public abstract HttpVersion getVersion();

    public String getRemoteIpAddress() {
        return this.remoteIp;
    }

    protected abstract String getNetworkIpAddress();

    public List<String> getRequestHops() {
        List<String> hops = new ArrayList<>();

        // If we are expecting the request to be proxied and the X-Forwarded-For header
        // is present then we parse it.
        if (this.isProxied) {
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For
            List<String> forwardedForHeader = this.getHeaders().get("X-Forwarded-For");

            if (forwardedForHeader != null) {
                for (String list : forwardedForHeader) {
                    for (String hop : list.split(",")) {
                        hops.add(hop.trim());
                    }
                }
            }
        }

        // Add the final hop. If we aren't proxied then this has the effect of adding
        // the actual IP address to the list.
        String finalHop = this.getNetworkIpAddress();

        if (!hops.contains(finalHop)) {
            hops.add(finalHop);
        }

        return hops;
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("HttpSession(");

        sb.append("\n    method=").append(this.getMethod());
        sb.append("\n    version=").append(this.getVersion());
        sb.append("\n    uri=").append(this.getUri()).append(this.getQueryString());
        sb.append("\n    queryParameters=").append(this.getAllQueryParameters());
        sb.append("\n    headers=").append(this.getHeaders());
        sb.append("\n    host=").append(this.getHost());
        sb.append("\n    port=").append(this.getPort());
        sb.append("\n    remoteIpAddress=").append(this.getRemoteIpAddress());
        sb.append("\n    hops=").append(this.getRequestHops());

        sb.append("\n)");

        return sb.toString();
    }

}