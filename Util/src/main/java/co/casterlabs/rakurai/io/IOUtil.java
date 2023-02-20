package co.casterlabs.rakurai.io;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.DataSize;
import lombok.NonNull;

public class IOUtil {
    public static int DEFAULT_BUFFER_SIZE = (int) DataSize.KILOBYTE.toBytes(16);

    public static void safeClose(@Nullable Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                try {
                    closeable.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void writeInputStreamToOutputStream(@NonNull InputStream source, @NonNull OutputStream dest) throws IOException {
        writeInputStreamToOutputStream(source, dest, DEFAULT_BUFFER_SIZE);
    }

    public static void writeInputStreamToOutputStream(@NonNull InputStream source, @NonNull OutputStream dest, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        int read = 0;

        while ((read = source.read(buffer)) != -1) {
            dest.write(buffer, 0, read);
        }

        dest.flush();

        source.close();
        dest.close();
    }

    public static void writeInputStreamToOutputStream(@NonNull InputStream source, @NonNull OutputStream dest, long length, int maxBufferSize) throws IOException {
        // Buffer allocation can be taxing, so we cheat and will only
        // allocate the required buffer size if possible.
        int bufferSize = (length > maxBufferSize) ? maxBufferSize : (int) length;

        writeInputStreamToOutputStream(source, dest, bufferSize);
    }

    public static byte[] readInputStreamBytes(@NonNull InputStream source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeInputStreamToOutputStream(source, out);

        source.close();

        return out.toByteArray();
    }

    public static String readString(@NonNull InputStream source, int length) throws IOException {
        return readInputStreamString(source, length, StandardCharsets.UTF_8);
    }

    public static String readInputStreamString(@NonNull InputStream source, int length, @NonNull Charset sourceCharset) throws IOException {
        byte[] bytes = new byte[length];

        source.read(bytes);
        source.close();

        return new String(bytes, sourceCharset);
    }

    public static String readString(@NonNull InputStream source) throws IOException {
        return readInputStreamString(source, StandardCharsets.UTF_8);
    }

    public static String readInputStreamString(@NonNull InputStream source, @NonNull Charset sourceCharset) throws IOException {
        byte[] bytes = readInputStreamBytes(source);

        return new String(bytes, sourceCharset);
    }

}
