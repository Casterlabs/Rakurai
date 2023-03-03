package co.casterlabs.rakurai.io.http.server;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import co.casterlabs.rakurai.io.http.HttpSession;

public class HttpServerUtil {

    public static boolean shouldCompress(String mimeType) {
        // Source: https://cdn.jsdelivr.net/gh/jshttp/mime-db@master/db.json

        // Literal text.
        if (mimeType.startsWith("text/")) return true;
        if (mimeType.endsWith("+text")) return true;

        // Compressible data types.
        if (mimeType.endsWith("json")) return true;
        if (mimeType.endsWith("xml")) return true;
        if (mimeType.endsWith("csv")) return true;

        // Other.
        if (mimeType.equals("application/javascript") || mimeType.equals("application/x-javascript")) return true;
        if (mimeType.equals("image/bmp")) return true;
        if (mimeType.equals("image/vnd.adobe.photoshop")) return true;
        if (mimeType.equals("image/vnd.microsoft.icon") || mimeType.equals("image/x-icon")) return true;
        if (mimeType.equals("application/tar") || mimeType.equals("application/x-tar")) return true;
        if (mimeType.equals("application/wasm")) return true;

        return false;
    }

    public static List<String> getAcceptedEncodings(HttpSession session) {
        List<String> accepted = new LinkedList<>();

        for (String value : session.getHeaders().getOrDefault("Accept-Encoding", Collections.emptyList())) {
            String[] split = value.split(", ");
            for (String encoding : split) {
                accepted.add(encoding.toLowerCase());
            }
        }

        return accepted;
    }

}
