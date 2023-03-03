package co.casterlabs.rakurai.io.http.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

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
    public InputStream getRequestBodyStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Map<String, String> parseFormBody() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final String getRawMethod() {
        return "GET";
    }

}
