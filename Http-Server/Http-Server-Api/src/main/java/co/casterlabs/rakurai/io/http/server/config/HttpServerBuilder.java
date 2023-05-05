package co.casterlabs.rakurai.io.http.server.config;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import co.casterlabs.rakurai.io.http.TLSVersion;
import co.casterlabs.rakurai.io.http.server.HttpListener;
import co.casterlabs.rakurai.io.http.server.HttpServer;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class HttpServerBuilder {
    /* General */
    protected @NonNull String hostname = "0.0.0.0";

    protected int port = 80;

    protected SSLConfiguration ssl;

    protected boolean behindProxy = false;

    /* Protocols */
    @Deprecated
    protected boolean SPDYEnabled = false;

    protected boolean http2Enabled = true;

    /* Abstract things */
    public abstract HttpServer build(@NonNull HttpListener listener) throws IOException;

    public abstract HttpServer buildSecure(@NonNull HttpListener listener) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException;

    public abstract HttpServerImplementation getImplementation();

    /* Util */

    @Override
    public HttpServerBuilder clone() {
        HttpServerBuilder nu = get(this.getImplementation());
        nu.hostname = this.hostname;
        nu.port = this.port;
        if (this.ssl != null) nu.ssl = this.ssl.clone();
        nu.behindProxy = this.behindProxy;
        nu.SPDYEnabled = this.SPDYEnabled;
        nu.http2Enabled = this.http2Enabled;
        return nu;
    }

    protected String[] convertTLS() {
        TLSVersion[] tls = this.ssl.getEnabledTlsVersions();
        String[] versions = new String[tls.length];

        for (int i = 0; i != tls.length; i++) {
            versions[i] = tls[i].getRuntimeName();
        }

        return versions;
    }

    protected void applyDHSize() {
        // https://www.java.com/en/configure_crypto.html
        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#customizing_dh_keys
        System.setProperty("jdk.tls.ephemeralDHKeySize", String.valueOf(this.ssl.getDHSize()));
        String disabledAlgorithmsProperty = System.getProperty("jdk.tls.disabledAlgorithms", "DH keySize");
        String[] disabledAlgorithms = disabledAlgorithmsProperty.split(",");
        boolean replacedParameter = false;

        for (int i = 0; i != disabledAlgorithms.length; i++) {
            if (disabledAlgorithms[i].startsWith("DH keySize")) {
                replacedParameter = true;

                disabledAlgorithms[i] = "DH keySize < " + this.ssl.getDHSize();

                break;
            }
        }

        if (replacedParameter) {
            System.setProperty("jdk.tls.disabledAlgorithms", String.join(", ", disabledAlgorithms));
        } else {
            System.setProperty("jdk.tls.disabledAlgorithms", disabledAlgorithmsProperty + ", DH keySize < " + this.ssl.getDHSize());
        }
    }

    /* Builders */
    public static HttpServerBuilder get(@NonNull HttpServerImplementation implementation) {
        switch (implementation) {
            case NANO:
                return getNanoBuilder();

            case UNDERTOW:
                return getUndertowBuilder();

            case RAKURAI:
                return getRakuraiBuilder();
        }

        throw new IllegalArgumentException(); // Hush mr compiley.
    }

    public static HttpServerBuilder getNanoBuilder() {
        try {
            Class<?> clazz = Class.forName("co.casterlabs.rakurai.http.server.impl.nano.NanoHttpServerBuilder");
            return (HttpServerBuilder) clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to instantiate nanohttpd impl.", e);
        }
    }

    public static HttpServerBuilder getUndertowBuilder() {
        try {
            Class<?> clazz = Class.forName("co.casterlabs.rakurai.http.server.impl.undertow.UndertowHttpServerBuilder");
            return (HttpServerBuilder) clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to instantiate nanohttpd impl.", e);
        }
    }

    public static HttpServerBuilder getRakuraiBuilder() {
        try {
            Class<?> clazz = Class.forName("co.casterlabs.rakurai.http.server.impl.rakurai.RakuraiHttpServerBuilder");
            return (HttpServerBuilder) clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to instantiate nanohttpd impl.", e);
        }
    }

}
