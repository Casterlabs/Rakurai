package co.casterlabs.rakurai.impl.http.rakurai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpStatus;
import co.casterlabs.rakurai.io.http.HttpVersion;

public abstract class RHSProtocol {
    public static final Charset HEADER_CHARSET = Charset.forName(System.getProperty("rakurai.http.headercharset", "ISO-8859-1"));

//    private static final byte[] CONTINUE_STATUS = "HTTP/1.1 100 Continue\r\n\r\n".getBytes(HEADER_CHARSET);

    // @formatter:off
    private static final int MAX_METHOD_LENGTH = 512 /*b*/; // Also used for the http version.
    private static final int MAX_URI_LENGTH    =  64 /*kb*/ * 1024;
    private static final int MAX_HEADER_LENGTH =  16 /*kb*/ * 1024;
    // @formatter:on

    public static HttpSession accept(RakuraiHttpServer server, Socket client, BufferedInputStream in) throws IOException, RHSHttpException {
        // Request line
        int[] $currentLinePosition = new int[1]; // int pointer :D
        int[] $endOfLinePosition = new int[1]; // int pointer :D
        byte[] requestLine = readRequestLine(in, $endOfLinePosition);

        String method = readMethod(requestLine, $currentLinePosition, $endOfLinePosition[0]);
        String uri = readURI(requestLine, $currentLinePosition, $endOfLinePosition[0]);
        HttpVersion version = readVersion(requestLine, $currentLinePosition, $endOfLinePosition[0]);

        // Headers
        HeaderMap headers = // HTTP/0.9 doesn't have headers.
            version == HttpVersion.HTTP_0_9 ? //
                new HeaderMap.Builder().build() : readHeaders(in);

        switch (version) {
//            case HTTP_1_1:
//                if (!headers.containsKey("Host")) {
//                    throw new RHSHttpException(HttpStatus.adapt(400, "Missing Host header"));
//                }
//                client.getOutputStream().write(CONTINUE_STATUS); // Immediately write a CONTINUE so that the client knows we're a 1.1 server.
//                break;

            // s00n
            case HTTP_1_1:
            case HTTP_2_0:
            case HTTP_3_0:
                throw new RHSHttpException(HttpStatus.adapt(400, "Unsupported HTTP version"));

            default:
                break;
        }

        int indexOfQuery = uri.indexOf('?');
        String queryString = "";
        Map<String, List<String>> allQueryParameters = new HashMap<>();

        if (indexOfQuery != -1) {
            queryString = uri.substring(indexOfQuery);
            uri = uri.substring(0, indexOfQuery);
            parseAllQueryParameters(queryString, allQueryParameters);
        }

        // Copy the query parameters to a singleton map.
        // Also copy to another map, this time making the list unmodifiable.
        Map<String, List<String>> unmodQueryParameters = new HashMap<>();
        Map<String, String> queryParameters = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : allQueryParameters.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            unmodQueryParameters.put(key, Collections.unmodifiableList(values));

            if (!values.isEmpty()) {
                queryParameters.put(key, values.get(0));
            }
        }

        return new RHSHttpSession(
            headers,
            uri,
            queryString,
            Collections.unmodifiableMap(unmodQueryParameters),
            Collections.unmodifiableMap(queryParameters),
            -1, // server.getPort(),
            version,
            method,
            "127.0.0.1" // client.getInetAddress().getHostAddress()
        );
    }

    public static byte[] readRequestLine(BufferedInputStream in, int[] $endOfLinePosition) throws IOException, RHSHttpException {
        byte[] buffer = new byte[MAX_METHOD_LENGTH + MAX_URI_LENGTH + MAX_METHOD_LENGTH];
        int bufferWritePos = 0;
        while (true) {
            int readCharacter = in.read();

            if (readCharacter == -1) {
                throw new IOException("Reached end of stream before request line was fully read.");
            }

            // Convert the \r character to \n, dealing with the consequences if necessary.
            if (readCharacter == '\r') {
                readCharacter = '\n';

                // Peek at the next byte, if it's a \n then we need to consume it.
                in.mark(1);
                if (in.read() == '\n') {
                    in.reset();
                    in.skip(1);
                } else {
                    in.reset();
                }
            }

            if (readCharacter == '\n') {
                break; // End of method name, break!
            }

            buffer[bufferWritePos++] = (byte) (readCharacter & 0xff);
        }

        if (bufferWritePos == 0) {
            throw new RHSHttpException(HttpStatus.adapt(400, "Request line was blank"));
        }

        $endOfLinePosition[0] = bufferWritePos; // Update the pointer.
        return buffer;
    }

    public static String readMethod(byte[] buffer, int[] $currentLinePosition, int endOfLinePosition) throws IOException, RHSHttpException {
        final int startPos = $currentLinePosition[0];
        int bufferReadPos = startPos;
        int length = -1;
        while (true) {
            if (bufferReadPos == endOfLinePosition) {
                length = bufferReadPos - startPos - 1;
                break;
            }

            int readCharacter = buffer[bufferReadPos++];

            if (readCharacter == ' ') {
                length = bufferReadPos - startPos - 1;

                // Consume any trailing spaces.
                while (true) {
                    if (buffer[bufferReadPos] == ' ') {
                        bufferReadPos++;
                    } else {
                        break;
                    }
                }

                break; // End of method name, break!
            }
        }

        if (length <= 0) {
            // We will not send an ALLOW header.
            throw new RHSHttpException(HttpStatus.adapt(405, "Method was blank"));
        }

        $currentLinePosition[0] = bufferReadPos; // Update the pointer.
        return new String(buffer, startPos, length, HEADER_CHARSET);
    }

    public static String readURI(byte[] buffer, int[] $currentLinePosition, int endOfLinePosition) throws IOException, RHSHttpException {
        final int startPos = $currentLinePosition[0];
        int bufferReadPos = startPos;
        int length = -1;
        while (true) {
            if (bufferReadPos == endOfLinePosition) {
                length = bufferReadPos - startPos - 1;
                break;
            }

            int readCharacter = buffer[bufferReadPos++];

            if (readCharacter == ' ') {
                length = bufferReadPos - startPos - 1;

                // Consume any trailing spaces.
                while (true) {
                    if (buffer[bufferReadPos] == ' ') {
                        bufferReadPos++;
                    } else {
                        break;
                    }
                }

                break; // End of URI, break!
            }
        }

        if (length <= 0) {
            throw new RHSHttpException(HttpStatus.adapt(404, "No URI specified"));
        }

        String uri = new String(buffer, startPos, length, HEADER_CHARSET);

        // Absolute URLs must be accepted but ignored.
        if (uri.startsWith("http://")) {
            uri = uri.substring(uri.indexOf('/', "http://".length()));
        } else if (uri.startsWith("https://")) {
            uri = uri.substring(uri.indexOf('/', "https://".length()));
        }

        $currentLinePosition[0] = bufferReadPos; // Update the pointer.
        return uri;
    }

    public static HttpVersion readVersion(byte[] buffer, int[] $currentLinePosition, int endOfLinePosition) throws IOException, RHSHttpException {
        final int startPos = $currentLinePosition[0];
        String version = new String(buffer, startPos, endOfLinePosition - startPos, HEADER_CHARSET);

        try {
            return HttpVersion.fromString(version);
        } catch (IllegalArgumentException e) {
            throw new RHSHttpException(HttpStatus.adapt(400, "Unsupported HTTP version"));
        }
    }

    public static HeaderMap readHeaders(BufferedInputStream in) throws IOException {
        HeaderMap.Builder headers = new HeaderMap.Builder();

        byte[] keyBuffer = new byte[MAX_HEADER_LENGTH];
        int keyBufferWritePos = 0;

        byte[] valueBuffer = new byte[MAX_HEADER_LENGTH];
        int valueBufferWritePos = 0;

        boolean isCurrentLineBlank = true;
        boolean isBuildingHeaderKey = true;
        while (true) {
            int readCharacter = in.read();

            if (readCharacter == -1) {
                throw new IOException("Reached end of stream before headers were fully read.");
            }

            // Convert the \r character to \n, dealing with the consequences if necessary.
            if (readCharacter == '\r') {
                readCharacter = '\n';

                // Peek at the next byte, if it's a \n then we need to consume it.
                in.mark(1);
                if (in.read() == '\n') {
                    in.reset();
                    in.skip(1);
                } else {
                    in.reset();
                }
            }

            if (readCharacter == '\n') {
                if (isCurrentLineBlank) {
                    break; // A blank line after headers marks the end, so we break out.
                }

                // A header line that is a whitespace is a continuation of the previous header
                // line. Example of what we're looking for:
                /* X-My-Header: some-value-1,\r\n  */
                /*              some-value-2\r\n   */
                try {
                    in.mark(1);
                    if (in.read() == ' ') {
                        continue; // Keep on readin'
                    }
                } finally {
                    in.reset();
                }

                // Alright, we're done with this header.
                String headerKey = convertBufferToTrimmedString(keyBuffer, keyBufferWritePos);
                String headerValue = convertBufferToTrimmedString(valueBuffer, valueBufferWritePos);
                headers.put(headerKey, headerValue);

                // Cleanup / Reset for the next header.
                isCurrentLineBlank = true;
                isBuildingHeaderKey = true;
                keyBufferWritePos = 0;
                valueBufferWritePos = 0;
                continue;
            }

            // Okay, line isn't blank. Let's buffer some data!
            isCurrentLineBlank = false;

            if (readCharacter == ':' && isBuildingHeaderKey) { // Note that colons are allowed in header values.
                // Time to switch over to building the value.
                isBuildingHeaderKey = false;
                continue;
            }

            byte b = (byte) (readCharacter & 0xff);

            if (isBuildingHeaderKey) {
                keyBuffer[keyBufferWritePos++] = b;
            } else {
                valueBuffer[valueBufferWritePos++] = b;
            }
        }

        return headers.build();
    }

    private static String convertBufferToTrimmedString(byte[] buffer, int bufferLength) {
        int startPos = 0;

        // Trim the leading.
        for (; startPos < bufferLength; startPos++) {
            byte ch = buffer[startPos];

            // Skip spaces.
            if (ch == ' ') {
                continue;
            }

            break;
        }

        int endPos = bufferLength;
        for (; endPos > 0; endPos--) {
            byte ch = buffer[endPos];

            // Skip spaces.
            if (ch == ' ') {
                continue;
            }

            break;
        }

        int length = endPos - startPos;
        return new String(buffer, startPos, length, HEADER_CHARSET);
    }

    private static void parseAllQueryParameters(String queryString, Map<String, List<String>> allQueryParameters) {
        // Magic.
        Arrays
            .stream(queryString.substring(1).split("&"))
            .map((it) -> {
                try {
                    int eqIdx = it.indexOf("=");

                    if (eqIdx == -1) {
                        return new SimpleImmutableEntry<>(
                            URLDecoder.decode(it, "UTF-8"),
                            null
                        );
                    }

                    String key = it.substring(0, eqIdx);
                    String value = it.substring(eqIdx + 1);

                    return new SimpleImmutableEntry<>(
                        URLDecoder.decode(key, "UTF-8"),
                        URLDecoder.decode(value, "UTF-8")
                    );
                } catch (UnsupportedEncodingException ignored) {
                    return null;
                }
            })
            .collect(
                Collectors.groupingBy(
                    SimpleImmutableEntry::getKey,
                    HashMap::new,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                )
            )
            .forEach((key, values) -> {
                @SuppressWarnings("unchecked")
                List<String> actualValues = (List<String>) values
                    .parallelStream()
                    .filter((v) -> v != null)
                    .collect(Collectors.toList());

                allQueryParameters.put(key, actualValues);
            });
    }

}
