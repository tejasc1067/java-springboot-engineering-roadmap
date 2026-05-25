import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MultiThreadedEchoServer {

    static final int PORT = 19002;
    static final int POOL_SIZE = 8;

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

        // Run for a bounded time so the example terminates. In production this would be a
        // signal-driven shutdown.
        Thread serverThread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                server.setSoTimeout(500);              // accept() returns periodically so we can check shutdown
                System.out.println("MultiThreadedEchoServer listening on port " + PORT + " (pool=" + POOL_SIZE + ")");
                long deadline = System.currentTimeMillis() + 3000;     // demo: 3 seconds
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Socket client = server.accept();
                        pool.submit(() -> handle(client));
                    } catch (java.net.SocketTimeoutException ignored) {
                        // loop back and check the deadline
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "accept-loop");
        serverThread.start();

        // Quick self-test: connect twice in parallel to prove threaded handling works.
        Thread c1 = clientThread("client-1", "first");
        Thread c2 = clientThread("client-2", "second");
        c1.start(); c2.start();
        c1.join(); c2.join();

        serverThread.join();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("done.");
    }

    static void handle(Socket client) {
        try (Socket s = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                out.println(Thread.currentThread().getName() + " echoes: " + line);
                if (line.equals("bye")) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Thread clientThread(String name, String prefix) {
        return new Thread(() -> {
            try (Socket s = new Socket("localhost", PORT);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                Thread.sleep(100);              // let server be ready
                for (int i = 1; i <= 3; i++) {
                    out.println(prefix + "-" + i);
                    System.out.println(name + " got: " + in.readLine());
                }
                out.println("bye");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, name);
    }
}
