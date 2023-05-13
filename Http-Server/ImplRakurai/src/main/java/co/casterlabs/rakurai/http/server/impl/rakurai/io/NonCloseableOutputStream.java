package co.casterlabs.rakurai.http.server.impl.rakurai.io;

import java.io.IOException;
import java.io.OutputStream;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NonCloseableOutputStream extends OutputStream {
    private final OutputStream out;

    @Override
    public void close() throws IOException {} // No OP

    @Override
    public void write(int b) throws IOException {
        this.out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (b.length == 0) return;
        this.out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) return;
        this.out.write(b, off, len);
    }

}
