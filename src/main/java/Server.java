import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.NameValuePair;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.net.URLDecoder;

public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final int LIMIT = 4096; //лимит на запрос
    public static final List<String> allowedMethods = Arrays.asList(GET, POST);
    private ServerSocket serverSocket;
    private Socket socket;
    private List<String> validPaths;
    private Queue<Thread> threadPool;

    Server(List<String> validPaths, int port, int cpacityThreadPool) {
        //threadPool = new ConcurrentLinkedQueue<Thread>();
        threadPool = new ArrayBlockingQueue<>(cpacityThreadPool, true);
        this.validPaths = validPaths;
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void startServer() {
        while (true)
            try {
                socket = serverSocket.accept();
                Thread handler = new Thread(new Handler());
                threadPool.add(handler);
                handler.start();
            } catch (IllegalStateException e) {
                System.out.println("ThreadPool is full");
                response503();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
    }

    class Handler implements Runnable {
        @Override
        public void run() {
            try {
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                handlerRequest(in, out);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void handlerRequest(BufferedInputStream in, BufferedOutputStream out) {
        try {
            in.mark(LIMIT);
            final byte[] buffer = new byte[LIMIT];
            final int read = in.read(buffer);
            // request line
            final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                badRequest(out);
                return; // just close socket
            }
            final String[] requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                return; // just close socket
            }
            //method
            final String method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                badRequest(out);
                return;
            }
            System.out.println(method);
            //path
            final String path = requestLine[1];
            if (!path.startsWith("/")) {
                badRequest(out);
                return;
            }
            if (validPaths.contains(path)) {
                response(out, path);
                return;
            }
            System.out.println(path);

            if (method.equals(GET)) {
                //List<KeyValueParam> listKeyValueParam = getQueryParams(path);
                final int numberCh = path.indexOf('?');
                final String requestKeyValue = path.substring(numberCh + 1);
                List<NameValuePair> paramsGet = getParams(requestKeyValue);
                System.out.println(paramsGet);
                //System.out.println(getParam(paramsGet, "value"));
            }
            //protocol
            final String procolVersion = requestLine[2];
            System.out.println(procolVersion);
            //headers
            final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final int headersStart = requestLineEnd + requestLineDelimiter.length;
            final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                return; // just close socket
            }
            final byte[] headersBytes = readNumberBytes(buffer, headersStart, headersEnd);
            final List<String> headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println("Headers: " + headers.toString());
            if (!method.equals(GET)) {
                // вычитываем Content-Length, чтобы прочитать body
                final Optional<String> contentLength = extractHeader(headers, "Content-Length");
                final Optional<String> contentType = extractHeader(headers, "Content-Type");
                if (contentLength.isPresent()) {
                    //final int length = Integer.parseInt(contentLength.get());
                    final byte[] bodyBytes = readNumberBytes(buffer, headersEnd + headersDelimiter.length, read);
                    final String body = new String(bodyBytes);
                    System.out.println(body);
                    if (contentType.get().equals("application/x-www-form-urlencoded")) {
                        List<NameValuePair> paramsPost = getParams(body);
                        System.out.println(paramsPost);
                        //System.out.println(getParam(paramsPost, "value"));
                    }
                }
            }
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void response(BufferedOutputStream out, String path) {
        try {
            final Path filePath = FileSystems.getDefault().getPath(".", "public", path);
            final String mimeType = Files.probeContentType(filePath);
            // special case for classic
            if (path.equals("/classic.html")) {
                final String template = Files.probeContentType(filePath);
                final byte[] content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                return;
            }
            final long length;
            length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void badRequest(BufferedOutputStream out) {
        try {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void response503() {
        try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            out.write((
                    "HTTP/1.1 503 Service Unavailable\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
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

    public static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    public static class KeyValueParam {
        private String key;
        private String value;
        KeyValueParam(String key, String value) {
            this.key = key;
            this.value = value;
        }
        public String getKey() {
            return key;
        }
        public String getValue(String key) {
            return value;
        }
        @Override
        public String toString() {
            return "(key) " + key + " = (value) " + value;
        }
    }

    public List<NameValuePair> getParams(String requestKeyValue) {
        try {
            /*
            final String[] params = requestKeyValue.split("&");
            for (String param : params) {
                int equalsCh = param.indexOf('=');
                String key = URLDecoder.decode(param.substring(0, equalsCh));
                String value = URLDecoder.decode(param.substring(equalsCh + 1));
                if (key != null & value != null) listKeyValueParam.add(new KeyValueParam(key, value));
            }
            System.out.println(listKeyValueParam.toString());
            */
            List<NameValuePair> params = URLEncodedUtils.parse(requestKeyValue, StandardCharsets.UTF_8);
            return params;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public String getParam(List<NameValuePair> params, String key) {
        /*
        for (KeyValueParam param : params) {
            if ((param.getKey()).equals(key)) {
                String value = param.getValue(key);
                return value;
            }
        }
         */
        for (NameValuePair param : params) {
            if ((param.getName()).equals(key))
                return param.getValue();
        }
        return null;
    }
}