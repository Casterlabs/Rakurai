package co.casterlabs.rakurai.io.http.server;

import java.io.IOException;

public interface HttpServer {

    public void start() throws IOException;

    public void stop() throws IOException;

    public int getPort();

    public boolean isAlive();

    public HttpServerImplementation getImplementation();

}
