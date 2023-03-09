package co.casterlabs.rakurai.http.server.impl.rakurai.io;

import java.io.IOException;
import java.io.OutputStream;

import co.casterlabs.rakurai.http.server.impl.rakurai.protocol.RHSProtocol;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HttpChunkedOutputStream extends OutputStream {
    private OutputStream out;

    @Override
    public void write(int b) throws IOException {
        this.out.write('1');
        RHSProtocol.writeString("\r\n", this.out);
        this.out.write(b);
        RHSProtocol.writeString("\r\n", this.out);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (b.length == 0) return;

        RHSProtocol.writeString(Integer.toHexString(b.length), this.out);
        RHSProtocol.writeString("\r\n", this.out);
        this.out.write(b);
        RHSProtocol.writeString("\r\n", this.out);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) return;

        RHSProtocol.writeString(Integer.toHexString(len), this.out);
        RHSProtocol.writeString("\r\n", this.out);
        this.out.write(b, off, len);
        RHSProtocol.writeString("\r\n", this.out);
    }

    @Override
    public void close() throws IOException {
        RHSProtocol.writeString("0\r\n\r\n", this.out);
        // Don't actually close the outputstream.
    }
}
