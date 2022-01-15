package co.casterlabs.rakurai.io.http.websocket;

import java.io.IOException;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.HttpSession;

public abstract class WebsocketSession extends HttpSession {

    @Override
    public boolean hasBody() {
        return false;
    }

    @Override
    public @Nullable byte[] getRequestBodyBytes() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> parseFormBody() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpMethod getMethod() {
        return HttpMethod.GET;
    }

}
