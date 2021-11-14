import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {
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
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                handlerRequest(in, out);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void handlerRequest(BufferedReader in, BufferedOutputStream out) {
        try {
            String requestLine = in.readLine();
            if (requestLine == null) return;
            System.out.println(requestLine);
            final String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                return; // just close socket
            }

            final String path = parts[1];
            if (!validPaths.contains(path)) {
                badRequest(out);
                return;
            }

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

            final long length = Files.size(filePath);
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
            System.out.println(e.getMessage());
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
        try(BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
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
