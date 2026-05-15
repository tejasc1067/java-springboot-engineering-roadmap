import java.net.ServerSocket;
import java.net.Socket;

public class BackendNetworkingWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        ServerSocket serverSocket =
                new ServerSocket(8080);

        System.out.println(
                "Backend Service Running"
        );

        while (true) {

            Socket client =
                    serverSocket.accept();

            System.out.println(
                    "Incoming Client Connection"
            );

            client.close();
        }
    }
}