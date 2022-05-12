package co.casterlabs.rakurai.io.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.websocket.WebsocketSession;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import lombok.Getter;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;
import xyz.e3ndr.fastloggingframework.logging.StringUtil;

public abstract class HttpSession {
    private final @Getter String requestId = UUID.randomUUID().toString();

    File logFile;
    PrintStream printOutput;
    boolean hasSessionErrored = false;

    private @Getter FastLogger logger;

    protected HttpSession(HttpServerBuilder config) {
        FastLogger realLogger = new FastLogger("Sora Session: " + this.requestId);

        boolean logsEnabled = !(this instanceof WebsocketSession) && (config.getLogsDir() != null);

        if (logsEnabled) {
            this.logFile = new File(config.getLogsDir(), this.requestId + ".httpexchange");

            try {
                this.logFile.createNewFile();
                this.printOutput = new PrintStream(new FileOutputStream(this.logFile));

                this.printOutput.println("\n\n---- Start of log ----");
            } catch (IOException e) {
                realLogger.fatal("Could not start request logging:\n%s", e);
            }
        }

        this.logger = new FastLogger(this.requestId) {
            @Override
            public FastLogger log(@NonNull LogLevel level, @Nullable Object object, @Nullable Object... args) {
                if (level.getPriority() <= LogLevel.WARNING.getPriority()) {
                    hasSessionErrored = true;
                    realLogger.log(level, object, args);
                }

                if (logsEnabled) {
                    String line = StringUtil.parseFormat(object, args);

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
        return this.getHeader("content-type");
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
        sb.append("\n    headers=").append(this.getHeaders());
        sb.append("\n    uri=").append(this.getUri()).append(this.getQueryString());

        sb.append("\n)");

        return sb.toString();
    }

}
