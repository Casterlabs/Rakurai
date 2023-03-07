package co.casterlabs.rakurai.impl.http.rakurai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpStatus;

public abstract class RHSProtocol {
    public static final Charset HEADER_CHARSET = Charset.forName(System.getProperty("rakurai.http.headercharset", "ISO-8859-1"));

    // @formatter:off
    static final int     MAX_METHOD_LENGTH = 512 /*b*/; // Also used for the http version.
    static final int     MAX_URL_LENGTH    =  64 /*kb*/ * 1024;
    static final int     MAX_HEADER_LENGTH =  16 /*kb*/ * 1024;
    // @formatter:on

    public HttpSession accept(RakuraiHttpServer server, Socket client, BufferedInputStream in) throws IOException, RHSHttpException {
        String method = readMethod(in);
//        String uri = readURI(in);
//        HttpVersion version = readVersion(in);
//        HeaderMap headers = readHeaders(in);

        return null;
//        return new RHSHttpSession(
//            headers,
//            "/", // TODO Path
//            "", // TODO Query
//            server.getPort(),
//            version,
//            method,
//            client.getInetAddress().getHostAddress()
//        );
    }

    public static String readMethod(BufferedInputStream in) throws IOException, RHSHttpException {
        byte[] buffer = new byte[MAX_METHOD_LENGTH];
        int bufferWritePos = 0;
        while (true) {
            int readCharacter = in.read();

            if (readCharacter == -1) {
                throw new IOException("Reached end of stream before method was read.");
            }

            if (readCharacter == ' ') {
                break; // End of method name, break!
            }

            buffer[bufferWritePos++] = (byte) (readCharacter & 0xff);
        }

        if (bufferWritePos == 0) {
            // We will not send an ALLOW header.
            throw new RHSHttpException(HttpStatus.adapt(405, "Method was blank."));
        }

        return new String(buffer, 0, bufferWritePos, HEADER_CHARSET);
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
                throw new IOException("Reached end of stream before headers were completely read.");
            }

            // Convert the \r character to \n, dealing with the consequences if necessary.
            if (readCharacter == '\r') {
                readCharacter = '\n';

                // Peek at the next byte, if it's a \n then we need to consume it.
                try {
                    in.mark(1);
                    if (in.read() == '\n') {
                        in.skip(1);
                    }
                } finally {
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

}
