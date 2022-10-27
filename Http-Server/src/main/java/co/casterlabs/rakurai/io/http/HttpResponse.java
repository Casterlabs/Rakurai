package co.casterlabs.rakurai.io.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import co.casterlabs.rakurai.CharStrings;
import co.casterlabs.rakurai.io.IOUtil;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.StringUtil;

@Getter
public class HttpResponse {
    /**
     * This response is used to signal to the server that we need to drop the
     * connection ASAP. (Assuming throwing {@link DropConnectionException} isn't
     * viable)
     */
    public static final HttpResponse NO_RESPONSE = HttpResponse.newFixedLengthResponse(StandardHttpStatus.NO_RESPONSE, new byte[0]);
    public static final HttpResponse INTERNAL_ERROR = HttpResponse.newFixedLengthResponse(StandardHttpStatus.INTERNAL_ERROR, new byte[0]);
    public static final byte[] EMPTY_BODY = new byte[0];

    private @Getter(AccessLevel.NONE) Map<String, String> headers = new HashMap<>();
    private @NonNull @Setter HttpStatus status;

    private ResponseContent content;

    public HttpResponse(@NonNull ResponseContent content, @NonNull HttpStatus status) {
        this.content = content;
        this.status = status;
    }

    /**
     * @deprecated This is only to be used internally.
     */
    @Deprecated
    public void finalizeResult(HttpSession session, HttpServerBuilder config, FastLogger serverLogger) {

        if (!session.hasSessionErrored) {
            return; // Do nothing, ignore it.
        }

        this.putHeader("X-Request-ID", session.getRequestId());

        if (session.printOutput == null) {
            serverLogger.info(
                "Request %s produced an error and was logged to console.\n" +
                    "Consider enabling logging in your config to get more detailed reports of incidents in the future.",
                session.getRequestId()
            );
            return;
        }

        // Start logigng.
        session.printOutput.println("\n\n---- End of log ----");

        // Request
        session.printOutput.println("\n\n---- Start of request ----");

        session.printOutput.format("%s %s\n\n", session.getMethod(), session.getUri());

        for (Map.Entry<String, List<String>> header : session.getHeaders().entrySet()) {
            for (String value : header.getValue()) {
                session.printOutput.format("%s: %s\n", header.getKey(), value);
            }
        }

        if (session.hasBody()) {
            try {
                byte[] body = session.getRequestBodyBytes();

                session.printOutput.write(body);
            } catch (IOException e) {
                session.printOutput.format("ERROR, UNABLE TO GET BODY. PRINTING STACK:\n", StringUtil.getExceptionStack(e));
            }
        }

        session.printOutput.println("\n\n---- End of request ----");

        // Response
        session.printOutput.println("\n\n---- Start of response ----");

        session.printOutput.format("%s: %s\n\n", this.status.getStatusCode(), this.status.getDescription());

        for (Entry<String, String> header : this.headers.entrySet()) {
            session.printOutput.format("%s: %s\n", header.getKey(), header.getValue());
        }

        if (this.content instanceof ByteResponse) {
            try {
                ByteResponse resp = (ByteResponse) this.content;
                session.printOutput.write(resp.response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            session.printOutput.print("<-- Stream response, not inspectable -->");
        }

        session.printOutput.println("\n\n---- End of response ----");

        // Write to file
        File logFile = new File(config.getLogsDir(), session.getRequestId() + ".httpexchange");

        try {
            Files.write(logFile.toPath(), session.printResult.toByteArray());
            serverLogger.info(
                "Request %s produced an error and was written to %s.",
                session.getRequestId(),
                logFile
            );
        } catch (IOException e) {
            serverLogger.severe(
                "Could not write log file for %s to %s:\n%s",
                session.getRequestId(),
                logFile,
                e
            );
            e.printStackTrace();
        }
    }

    /* ---------------- */
    /* Headers          */
    /* ---------------- */

    public HttpResponse setMimeType(String type) {
        return this.putHeader("content-type", type);
    }

    public HttpResponse putHeader(@NonNull String key, @NonNull String value) {
        this.headers.put(key, value);
        return this;
    }

    public HttpResponse putAllHeaders(@NonNull Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            this.headers.put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public boolean hasHeader(@NonNull String key) {
        return this.headers.containsKey(key);
    }

    public boolean removeHeader(@NonNull String key) {
        return this.headers.remove(key) != null;
    }

    public Map<String, String> getAllHeaders() {
        return this.headers;
    }

    /* ---------------- */
    /* Creating (Byte)  */
    /* ---------------- */

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status) {
        return newFixedLengthResponse(status, EMPTY_BODY);
    }

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status, @NonNull String body) {
        return newFixedLengthResponse(status, body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status, @NonNull char[] body) {
        return newFixedLengthResponse(status, CharStrings.strbytes(body));
    }

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status, @NonNull JsonElement json) {
        if ((json instanceof JsonObject) || (json instanceof JsonArray)) {
            byte[] body = json
                .toString(false)
                .getBytes(StandardCharsets.UTF_8);

            return newFixedLengthResponse(status, body)
                .setMimeType("application/json");
        } else {
            throw new IllegalArgumentException("Json must be an Object or Array.");
        }
    }

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status, @NonNull byte[] body) {
        HttpResponse response = new HttpResponse(
            new ByteResponse(body),
            status
        );

        return response;
    }

    /* ---------------- */
    /* Response (Stream) */
    /* ---------------- */

    public static HttpResponse newFixedLengthResponse(@NonNull HttpStatus status, @NonNull InputStream responseStream, long length) {
        HttpResponse response = new HttpResponse(
            new StreamResponse(responseStream, length),
            status
        );

        return response;
    }

    public static HttpResponse newFixedLengthFileResponse(@NonNull HttpStatus status, @NonNull File file) throws FileNotFoundException {
        FileInputStream fin = new FileInputStream(file);

        return newFixedLengthResponse(status, fin, file.length());
    }

    public static HttpResponse newFixedLengthFileResponse(@NonNull HttpStatus status, @NonNull File file, long skip, long length) throws FileNotFoundException, IOException {
        FileInputStream fin = new FileInputStream(file);

        fin.skip(skip);

        return newFixedLengthResponse(status, fin, length);
    }

    public static HttpResponse newChunkedResponse(@NonNull HttpStatus status, @NonNull InputStream responseStream) {
        return new HttpResponse(
            new StreamResponse(responseStream, -1),
            status
        );
    }

    /* ---------------- */
    /* Responses        */
    /* ---------------- */

    public static interface ResponseContent {

        public void write(OutputStream out) throws IOException;

        /**
         * @return any negative number for a chunked response.
         */
        public long getLength();

    }

    @AllArgsConstructor
    private static class StreamResponse implements ResponseContent {
        private InputStream response;
        private long length;

        @Override
        public void write(OutputStream out) throws IOException {
            // If we have to, use the less efficient response format.
            // Otherwise, we want to use the smallest buffer possible (Saves cpu).
            boolean isInefficient = (this.length == -1) || (this.length > Integer.MAX_VALUE);

            if (isInefficient) {
                IOUtil.writeInputStreamToOutputStream(this.response, out);
            } else {
                IOUtil.writeInputStreamToOutputStream(
                    this.response,
                    out,
                    this.length,
                    IOUtil.DEFAULT_BUFFER_SIZE
                );
            }
        }

        @Override
        public long getLength() {
            return this.length;
        }

    }

    @AllArgsConstructor
    private static class ByteResponse implements ResponseContent {
        private byte[] response;

        @Override
        public void write(OutputStream out) throws IOException {
            out.write(this.response);
        }

        @Override
        public long getLength() {
            return this.response.length;
        }

    }

}
