import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer {

    static final int PORT = 19001;

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("EchoServer listening on port " + PORT);
            System.out.println("Run EchoClient in another shell, or press Ctrl-C to stop.");

            // For demo purposes, handle ONE connection then exit so the example terminates.
            try (Socket client = server.accept()) {
                System.out.println("client connected: " + client.getRemoteSocketAddress());
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("got:  " + line);
                    out.println("echo: " + line);
                    if (line.equals("bye")) break;
                }
                System.out.println("client disconnected");
            }
        }
    }
}
