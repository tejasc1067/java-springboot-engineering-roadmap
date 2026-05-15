import java.io.*;
import java.net.Socket;

public class SocketMessageExchangeExample {

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
                "Backend Request"
        );

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream()
                        )
                );

        String response =
                reader.readLine();

        System.out.println(
                "Response: " + response
        );

        socket.close();
    }
}