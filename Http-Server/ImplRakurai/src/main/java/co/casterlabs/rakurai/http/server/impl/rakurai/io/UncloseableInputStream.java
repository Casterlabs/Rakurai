package co.casterlabs.rakurai.http.server.impl.rakurai.io;

import java.io.IOException;
import java.io.InputStream;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UncloseableInputStream extends InputStream {
    private InputStream in;

    @Override
    public void close() throws IOException {} // NOOP

    @Override
    public int read() throws IOException {
        return this.in.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return this.in.read(b);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return this.in.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return this.in.skip(n);
    }

    @Override
    public int available() throws IOException {
        return this.in.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        this.in.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        this.in.reset();
    }

    @Override
    public boolean markSupported() {
        return this.in.markSupported();
    }

}
