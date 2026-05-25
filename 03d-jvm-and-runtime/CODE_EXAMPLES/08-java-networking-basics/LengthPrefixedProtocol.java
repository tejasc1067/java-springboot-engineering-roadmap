import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class LengthPrefixedProtocol {

    static final int PORT = 19003;

    public static void main(String[] args) throws Exception {
        // Run server + client in the same JVM for a self-contained demo.
        Thread server = new Thread(LengthPrefixedProtocol::runServer, "server");
        server.start();
        Thread.sleep(100);                      // give the server a moment to bind

        try (Socket s = new Socket("localhost", PORT);
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {

            sendMessage(out, "hello");
            sendMessage(out, "this is a longer message that exercises the length prefix correctly");
            sendMessage(out, "bye");

            for (int i = 0; i < 3; i++) {
                System.out.println("client read: " + readMessage(in));
            }
        }
        server.join();
    }

    static void runServer() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            try (Socket client = server.accept();
                 InputStream in = client.getInputStream();
                 OutputStream out = client.getOutputStream()) {
                while (true) {
                    String msg = readMessage(in);
                    System.out.println("server got: " + msg);
                    sendMessage(out, "echo: " + msg);
                    if (msg.equals("bye")) break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Wire format: 4-byte big-endian length, then `length` UTF-8 bytes.
    static void sendMessage(OutputStream out, String s) throws IOException {
        byte[] body = s.getBytes(StandardCharsets.UTF_8);
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(body.length);
        d.write(body);
        d.flush();
    }

    static String readMessage(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        int len = d.readInt();
        byte[] body = new byte[len];
        // readFully loops until ALL len bytes arrive -- read() alone can return short.
        d.readFully(body);
        return new String(body, StandardCharsets.UTF_8);
    }
}
