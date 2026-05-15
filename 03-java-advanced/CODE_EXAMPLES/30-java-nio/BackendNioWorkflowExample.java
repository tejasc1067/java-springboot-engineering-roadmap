import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.net.InetSocketAddress;

public class BackendNioWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        Selector selector =
                Selector.open();

        ServerSocketChannel serverChannel =
                ServerSocketChannel.open();

        serverChannel.bind(
                new InetSocketAddress(9090)
        );

        serverChannel.configureBlocking(false);

        serverChannel.register(
                selector,
                serverChannel.validOps()
        );

        System.out.println(
                "Scalable Backend NIO Service Running"
        );
    }
}