import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class EchoClient {

    static final String HOST = "localhost";
    static final int PORT = 19001;

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000);           // give up if no data in 5s
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            for (String msg : new String[] { "hello", "another line", "bye" }) {
                out.println(msg);
                String reply = in.readLine();
                System.out.println("sent='" + msg + "', got='" + reply + "'");
            }
        }
    }
}
