import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class SingleThreadedNioServer {

    static final int PORT = 19005;
    static final CountDownLatch SERVER_READY = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Thread server = new Thread(SingleThreadedNioServer::runServer, "nio-server");
        server.start();
        SERVER_READY.await();           // wait until the server has bound the port

        // Spin up several clients in parallel. ONE server thread serves them all.
        int clientCount = 5;
        Thread[] clients = new Thread[clientCount];
        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            clients[i] = new Thread(() -> runClient(id), "client-" + i);
            clients[i].start();
        }
        for (Thread t : clients) t.join();
        Thread.sleep(300);              // let server drain
        server.interrupt();
        server.join();
        System.out.println("done.");
    }

    static void runServer() {
        try (Selector selector = Selector.open();
             ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(PORT));
            ssc.configureBlocking(false);
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("[server] selector loop on port " + PORT
                + ", one thread: " + Thread.currentThread().getName());
            SERVER_READY.countDown();     // signal clients that the port is bound

            ByteBuffer buf = ByteBuffer.allocate(256);
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                selector.select(200);                // wait up to 200ms for ready channels
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();                      // selector won't remove for us
                    if (key.isAcceptable()) {
                        SocketChannel client = ssc.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                        System.out.println("[server] accepted " + client.getRemoteAddress());
                    } else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        buf.clear();
                        int n = client.read(buf);
                        if (n < 0) {                  // peer closed
                            client.close();
                            continue;
                        }
                        buf.flip();
                        String msg = StandardCharsets.UTF_8.decode(buf).toString();
                        System.out.println("[server] got from " + client.getRemoteAddress() + ": " + msg.trim());
                        ByteBuffer reply = StandardCharsets.UTF_8.encode("echo: " + msg);
                        while (reply.hasRemaining()) client.write(reply);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void runClient(int id) {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress("localhost", PORT))) {
            ByteBuffer out = StandardCharsets.UTF_8.encode("hello from client " + id + "\n");
            while (out.hasRemaining()) ch.write(out);
            ByteBuffer in = ByteBuffer.allocate(256);
            ch.read(in);
            in.flip();
            System.out.println("[client " + id + "] reply: "
                + StandardCharsets.UTF_8.decode(in).toString().trim());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
