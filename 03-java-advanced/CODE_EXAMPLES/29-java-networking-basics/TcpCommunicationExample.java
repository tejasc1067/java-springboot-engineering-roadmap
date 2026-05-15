import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class TcpCommunicationExample {

    public static void main(String[] args)
            throws Exception {

        Socket socket =
                new Socket(
                        "localhost",
                        5000
                );

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream()
                        )
                );

        String response =
                reader.readLine();

        System.out.println(response);

        socket.close();
    }
}