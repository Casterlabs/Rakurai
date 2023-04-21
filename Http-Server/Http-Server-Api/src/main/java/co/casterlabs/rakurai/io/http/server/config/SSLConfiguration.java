package co.casterlabs.rakurai.io.http.server.config;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.io.http.TLSVersion;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Accessors(chain = true)
public class SSLConfiguration {
    private @NonNull TLSVersion[] enabledTlsVersions = TLSVersion.values();
    private @Nullable List<String> enabledCipherSuites;
    private @Setter int DHSize = 2048;

    private File keystoreLocation;
    private char[] keystorePassword;

    public SSLConfiguration(@NonNull File keystoreLocation, @NonNull char[] keystorePassword) {
        this.keystoreLocation = keystoreLocation;
        this.keystorePassword = keystorePassword;
    }

    public SSLConfiguration setEnabledTlsVersions(@NonNull TLSVersion... enabledTlsVersions) {
        this.enabledTlsVersions = enabledTlsVersions;
        return this;
    }

    public SSLConfiguration setEnabledCipherSuites(@Nullable String... enabledCipherSuites) {
        this.enabledCipherSuites = Arrays.asList(enabledCipherSuites);
        return this;
    }

    @Override
    public SSLConfiguration clone() {
        SSLConfiguration nu = new SSLConfiguration(this.keystoreLocation, this.keystorePassword);
        nu.enabledTlsVersions = this.enabledTlsVersions;
        nu.enabledCipherSuites = this.enabledCipherSuites;
        nu.DHSize = this.DHSize;
        return nu;
    }

}
