package co.casterlabs.rakurai.io.http.server;

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
    private @Setter @Nullable List<String> enabledCipherSuites;
    private @Setter int DHSize = 2048;

    private int port;

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

    public SSLConfiguration setEnabledCipherSuitesVersions(@Nullable String... enabledCipherSuites) {
        this.enabledCipherSuites = Arrays.asList(enabledCipherSuites);

        return this;
    }

}
