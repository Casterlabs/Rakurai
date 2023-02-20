package co.casterlabs.rakurai.io.http;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.io.http.HttpResponse.ByteResponse;
import co.casterlabs.rakurai.io.http.server.HttpServerBuilder;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.StringUtil;

/**
 * @deprecated This is only to be used internally.
 */
@Deprecated
public class Debugging {

    public static void finalizeResult(@Nullable HttpResponse response, @NonNull HttpSession session, @NonNull HttpServerBuilder config, @NonNull FastLogger serverLogger) {
        if (!session.hasSessionErrored) {
            return; // No error has occurred, ignore.
        }

        if (session.printOutput == null) {
            serverLogger.info(
                "Request %s produced an error and was logged to console.\n" +
                    "Consider enabling logging in your config to get more detailed reports of incidents in the future.",
                session.getRequestId()
            );

            if (response != null) {
                response.putHeader("X-Request-ID", session.getRequestId());
            }
            return;
        }

        // Start logigng.
        session.printOutput.println("\n\n---- End of log ----");

        // Request
        session.printOutput.println("\n\n---- Start of request ----");

        session.printOutput.format("%s %s\n\n", session.getMethod(), session.getUri());

        for (Map.Entry<String, List<String>> header : session.getHeaders().entrySet()) {
            for (String value : header.getValue()) {
                session.printOutput.format("%s: %s\n", header.getKey(), value);
            }
        }

        if (session.hasBody()) {
            try {
                byte[] body = session.getRequestBodyBytes();

                session.printOutput.write(body);
            } catch (IOException e) {
                session.printOutput.format("ERROR, UNABLE TO GET BODY. PRINTING STACK:\n", StringUtil.getExceptionStack(e));
            }
        }

        session.printOutput.println("\n\n---- End of request ----");

        // Response
        session.printOutput.println("\n\n---- Start of response ----");

        if (response == null) {
            session.printOutput.print("<-- Response not available -->");
        } else {
            session.printOutput.format("%s: %s\n\n", response.getStatus().getStatusCode(), response.getStatus().getDescription());

            response.putHeader("X-Request-ID", session.getRequestId());

            for (Entry<String, String> header : response.getHeaders().entrySet()) {
                session.printOutput.format("%s: %s\n", header.getKey(), header.getValue());
            }

            if (response.getContent() instanceof ByteResponse) {
                try {
                    ByteResponse resp = (ByteResponse) response.getContent();
                    session.printOutput.write(resp.getResponse());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                session.printOutput.print("<-- Stream response, not inspectable -->");
            }
        }

        session.printOutput.println("\n\n---- End of response ----");

        // Write to file
        File logFile = new File(config.getLogsDir(), session.getRequestId() + ".httpexchange");

        try {
            Files.write(logFile.toPath(), session.printResult.toByteArray());
            serverLogger.info(
                "Request %s produced an error and was written to %s.",
                session.getRequestId(),
                logFile
            );
        } catch (IOException e) {
            serverLogger.severe(
                "Could not write log file for %s to %s:\n%s",
                session.getRequestId(),
                logFile,
                e
            );
            e.printStackTrace();
        }
    }

}
