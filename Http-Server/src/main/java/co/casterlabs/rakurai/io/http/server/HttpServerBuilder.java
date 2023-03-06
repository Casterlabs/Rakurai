package co.casterlabs.rakurai.io.http.server;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.impl.http.nano.NanoHttpServerBuilder;
import co.casterlabs.rakurai.impl.http.undertow.UndertowHttpServerBuilder;
import co.casterlabs.rakurai.io.http.TLSVersion;
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

    protected @Nullable File logsDir = null;

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
        nu.logsDir = this.logsDir;
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

            default:
                throw new IllegalArgumentException(); // Hush mr compiley.
        }
    }

    public static HttpServerBuilder getNanoBuilder() {
        return new NanoHttpServerBuilder();
    }

    public static HttpServerBuilder getUndertowBuilder() {
        return new UndertowHttpServerBuilder();
    }

}
