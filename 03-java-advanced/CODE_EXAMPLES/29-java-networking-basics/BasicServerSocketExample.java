import java.net.ServerSocket;
import java.net.Socket;

public class BasicServerSocketExample {

    public static void main(String[] args)
            throws Exception {

        ServerSocket serverSocket =
                new ServerSocket(5000);

        System.out.println(
                "Server Started"
        );

        Socket socket =
                serverSocket.accept();

        System.out.println(
                "Client Connected"
        );

        socket.close();

        serverSocket.close();
    }
}