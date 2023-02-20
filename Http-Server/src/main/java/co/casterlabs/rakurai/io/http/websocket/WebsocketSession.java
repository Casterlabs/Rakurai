package co.casterlabs.rakurai.io.http.websocket;

import java.io.IOException;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.HttpSession;

public abstract class WebsocketSession extends HttpSession {

    @Override
    public final boolean hasBody() {
        return false;
    }

    @Override
    public final @Nullable byte[] getRequestBodyBytes() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Map<String, String> parseFormBody() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final HttpMethod getMethod() {
        return HttpMethod.GET;
    }

}
