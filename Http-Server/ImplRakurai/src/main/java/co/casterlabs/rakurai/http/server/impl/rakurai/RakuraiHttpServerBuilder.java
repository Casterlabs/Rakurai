package co.casterlabs.rakurai.http.server.impl.rakurai;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import co.casterlabs.rakurai.io.http.server.HttpServerImplementation;
import lombok.NonNull;

public class RakuraiHttpServerBuilder extends HttpServerBuilder {

    @Override
    public HttpServer build(@NonNull HttpListener listener) {
        return new RakuraiHttpServer(listener, this.clone());
    }

    @Override
    public HttpServer buildSecure(@NonNull HttpListener listener) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        return null; // TODO
    }

    @Override
    public HttpServerImplementation getImplementation() {
        return HttpServerImplementation.RAKURAI;
    }

}
