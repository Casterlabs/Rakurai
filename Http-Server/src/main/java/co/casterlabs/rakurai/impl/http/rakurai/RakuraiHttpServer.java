package co.casterlabs.rakurai.impl.http.rakurai;

import java.io.IOException;

import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@Getter
@RequiredArgsConstructor
public class RakuraiHttpServer implements HttpServer {
    private final FastLogger logger = new FastLogger("Rakurai RakuraiHttpServer");

    private final HttpListener listener;
    private final HttpServerBuilder config;

    @Override
    public void start() throws IOException {

    }

    @Override
    public void stop() throws IOException {

    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public int getPort() {
        return this.config.getPort();
    }

    @Override
    public HttpServerImplementation getImplementation() {
        return HttpServerImplementation.RAKURAI;
    }

}
