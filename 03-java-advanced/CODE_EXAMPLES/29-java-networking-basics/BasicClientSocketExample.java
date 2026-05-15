import java.io.PrintWriter;
import java.net.Socket;

public class BasicClientSocketExample {

    public static void main(String[] args)
            throws Exception {

        Socket socket =
                new Socket(
                        "localhost",
                        5000
                );

        PrintWriter writer =
                new PrintWriter(
                        socket.getOutputStream(),
                        true
                );

        writer.println(
                "Hello Server"
        );

        socket.close();
    }
}