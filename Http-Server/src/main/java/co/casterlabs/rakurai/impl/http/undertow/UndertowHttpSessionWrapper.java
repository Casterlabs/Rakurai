package co.casterlabs.rakurai.impl.http.undertow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.collections.HeaderMap;
import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.HttpSession;
import co.casterlabs.rakurai.io.http.HttpVersion;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

public class UndertowHttpSessionWrapper extends HttpSession {
    private HttpServerExchange exchange;
    private int port;

    private Map<String, List<String>> allQueryParameters = new HashMap<>();
    private Map<String, String> queryParameters = new HashMap<>();
    private HeaderMap headers;

    private byte[] body;

    public UndertowHttpSessionWrapper(HttpServerExchange exchange, int port) {
        this.exchange = exchange;
        this.port = port;

        io.undertow.util.HeaderMap headerMap = exchange.getRequestHeaders();

        HeaderMap.Builder builder = new HeaderMap.Builder();

        long headersIndex = headerMap.fastIterate();
        while (headersIndex != -1) {
            HeaderValues header = headerMap.fiCurrent(headersIndex);
            HttpString headerName = header.getHeaderName();

            builder.putAll(headerName.toString(), header);

            headersIndex = headerMap.fiNext(headersIndex);
        }

        this.headers = builder.build();

        for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
            this.allQueryParameters.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            this.queryParameters.put(entry.getKey(), entry.getValue().getFirst());
        }

    }

    // Request headers
    @Override
    public HeaderMap getHeaders() {
        return this.headers;
    }

    // URI
    @Override
    public String getUri() {
        return this.exchange.getRequestPath();
    }

    @Override
    public Map<String, List<String>> getAllQueryParameters() {
        return this.allQueryParameters;
    }

    @Override
    public Map<String, String> getQueryParameters() {
        return this.queryParameters;
    }

    @Override
    public String getQueryString() {
        if (this.exchange.getQueryString().isEmpty()) {
            return "";
        } else {
            return "?" + this.exchange.getQueryString();
        }
    }

    // Request body
    @Override
    public boolean hasBody() {
        return this.exchange.getRequestContentLength() != -1;
    }

    @Override
    public @Nullable byte[] getRequestBodyBytes() throws IOException {
        if (this.body == null) {
            long length = this.exchange.getRequestContentLength();

            if (length != -1) {
                this.body = new byte[(int) length];
                this.exchange.getInputStream().read(this.body, 0, (int) length);

                return this.body;
            } else {
                throw new IOException("No body was sent");
            }
        } else {
            return this.body;
        }
    }

    @Override
    public Map<String, String> parseFormBody() throws IOException {
        FormDataParser formDataParser = FormParserFactory.builder().build().createParser(this.exchange);

        if (formDataParser == null) {
            throw new IOException("No form data to parse.");
        } else {
            Map<String, String> files = new HashMap<>();

            this.exchange.startBlocking();

            FormData formData = formDataParser.parseBlocking();

            for (String data : formData) {
                for (FormData.FormValue formValue : formData.get(data)) {
                    files.put(data, formValue.getValue());
                    break;
                }
            }

            return files;
        }
    }

    // Server Info
    @Override
    public String getHost() {
        return this.getHeader("host");
    }

    @Override
    public int getPort() {
        return this.port;
    }

    // Misc
    @Override
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(this.exchange.getRequestMethod().toString());
    }

    @Override
    public HttpVersion getVersion() {
        return HttpVersion.valueOf(this.exchange.getProtocol().toString().replace('/', '_').replace('.', '_'));
    }

    @Override
    public String getRemoteIpAddress() {
        return this.exchange.getSourceAddress().getHostString();
    }

}
