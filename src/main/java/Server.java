import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final List<String> allowedMethods = Arrays.asList(GET, POST);
    private ExecutorService executorService;
    //private File directoryPublic = new File("public");
    final List<String> validPaths = Arrays.asList("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");

    Server(int cpacityThreadPool) {
        this.executorService = Executors.newFixedThreadPool(cpacityThreadPool);
    }

    public void startServer(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port);) {
            while (true) {
                final Socket socket = serverSocket.accept();
                executorService.submit(() -> handlerRequest(socket));
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void handlerRequest(Socket socket) {
        try (
                final BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            Request request = Request.getRequest(in);
            if (!allowedMethods.contains(request.getMethod())) {
                badRequest(out);
                return;
            }
            if (validPaths.contains(request.getPath())) {
                response(out, request.getPath());
                return;
            }
            System.out.println(request.getheaders());
            if (request.getMethod().equals(GET)) {
                request.getQueryParams();
                System.out.println("value = " + request.getParam("value"));
                if (validPaths.contains(request.getRequestNewPath()))
                    response(out, request.getRequestNewPath());
            } else {
                request.getPostParams();
                System.out.println("value = " + request.getParam("value"));
                if (validPaths.contains(request.getPath()))
                    response(out, request.getPath());
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
                final byte[] byteTemplate = Files.readAllBytes(filePath);
                ByteArrayOutputStream byteArrayOutputStreamut = new ByteArrayOutputStream();
                byteArrayOutputStreamut.write(byteTemplate);
                String template = byteArrayOutputStreamut.toString();
                System.out.println(template);
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
                //return;
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

    public void response503(BufferedOutputStream out) {
        try {
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
}