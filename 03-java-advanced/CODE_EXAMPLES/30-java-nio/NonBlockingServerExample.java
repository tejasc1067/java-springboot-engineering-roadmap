import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class NonBlockingServerExample {

    public static void main(String[] args)
            throws Exception {

        ServerSocketChannel serverChannel =
                ServerSocketChannel.open();

        serverChannel.bind(
                new InetSocketAddress(8080)
        );

        serverChannel.configureBlocking(false);

        System.out.println(
                "Non-Blocking Server Started"
        );

        while (true) {

            if (serverChannel.accept() != null) {

                System.out.println(
                        "Client Connected"
                );
            }
        }
    }
}