package co.casterlabs.rakurai.io.http.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import co.casterlabs.rakurai.io.http.HttpSession;

public class HttpServerUtil {

    public static final List<String> compressibleMimes = Arrays.asList(
        "text/plain",
        "text/html",
        "image/svg+xml",
        "application/xml",
        "application/json"
    );

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
