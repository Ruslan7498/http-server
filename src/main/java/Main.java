public class Main {
    public static final int PORT = 8000;
    public static final int CAPACITY_THREAD_POOL = 64;

    public static void main(String[] args) {
        final Server server = new Server(CAPACITY_THREAD_POOL);
        server.startServer(PORT);
    }
}