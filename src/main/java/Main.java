import java.util.Arrays;
import java.util.List;

public class Main {
    public static final int PORT = 8000;
    public static final int CAPACITY_THREAD_POOL = 64;

    public static void main(String[] args) {
        final List<String> validPaths = Arrays.asList("/index.html", "/spring.svg", "/spring.png",
                "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
                "/classic.html", "/events.html", "/events.js", "/default-get.html");

        final Server server = new Server(validPaths, PORT, CAPACITY_THREAD_POOL);
        server.startServer();
    }
}


