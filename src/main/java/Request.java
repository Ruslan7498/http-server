import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Request {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final int LIMIT = 4096; //лимит на запрос
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String body;
    private List<NameValuePair> params;
    private String requestNewPath;

    public Request(String method, String path, Map<String, String> headers, String body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getheaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String getRequestNewPath() {
        return requestNewPath;
    }

    public static Request getRequest(BufferedInputStream in) {
        try {
            in.mark(LIMIT);
            final byte[] buffer = new byte[LIMIT];
            final int read = in.read(buffer);
            // requestline
            final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                throw new IOException("Error requestLine");
            }
            final String[] requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                throw new IOException("Error requestLine");
            }
            //method
            final String method = requestLine[0];
            if (!Server.allowedMethods.contains(method)) {
                throw new IOException("Method not found");
            }
            System.out.println(method);
            //path
            final String path = requestLine[1];
            if (!path.startsWith("/")) {
                throw new IOException("Path error");
            }
            System.out.println(path);
            //protocol
            final String procolVersion = requestLine[2];
            System.out.println(procolVersion);
            //headers
            final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final int headersStart = requestLineEnd + requestLineDelimiter.length;
            final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                throw new IOException("Heades error");
            }
            final byte[] headersBytes = readNumberBytes(buffer, headersStart, headersEnd);
            final Map<String, String> headers = new HashMap<>();
            String[] lineHeaders = new String(headersBytes).split("\r\n");
            for (int i = 0; i < lineHeaders.length; i++) {
                String line = lineHeaders[i];
                int j = line.indexOf(':');
                String key = line.substring(0, j);
                String value = line.substring(j + 2);
                headers.put(key, value);
            }
            //body
            String body = null;
            if (!method.equals(GET)) {
                if (headers.containsKey("Content-Length")) {
                    final byte[] bodyBytes = readNumberBytes(buffer, headersEnd + headersDelimiter.length, read);
                    body = new String(bodyBytes);
                }
            }
            //request
            return new Request(method, path, headers, body);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static byte[] readNumberBytes(byte[] buffer, int start, int max) {
        int bytes = max - start;
        byte[] buf = new byte[bytes];
        for (int a = 0; a < bytes; a++) {
            byte b = buffer[start];
            buf[a] = b;
            start++;
        }
        return buf;
    }

    public void getQueryParams() {
        try {
            final int numberCh = this.path.indexOf('?');
            final String requestKeyValue = this.path.substring(numberCh + 1);
            requestNewPath = this.path.substring(0, numberCh);
            params = URLEncodedUtils.parse(requestKeyValue, StandardCharsets.UTF_8);
            System.out.println(params);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void getPostParams() {
        try {
            params = URLEncodedUtils.parse(this.body, StandardCharsets.UTF_8);
            System.out.println(params);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public List<String> getParam(String key) {
        List<String> listParams = new ArrayList<>();
        for (NameValuePair param : params) {
            if ((param.getName()).equals(key))
                listParams.add(param.getValue());
        }
        return listParams;
    }
}