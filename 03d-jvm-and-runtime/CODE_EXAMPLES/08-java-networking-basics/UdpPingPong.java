import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UdpPingPong {

    static final int SERVER_PORT = 19004;

    public static void main(String[] args) throws Exception {
        Thread server = new Thread(UdpPingPong::runServer, "udp-server");
        server.start();
        Thread.sleep(100);

        // Client: send "ping", wait for "pong".
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2000);          // critical for UDP -- no connection to detect peer death

            byte[] outBytes = "ping".getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName("localhost");
            client.send(new DatagramPacket(outBytes, outBytes.length, addr, SERVER_PORT));

            byte[] inBuf = new byte[1024];
            DatagramPacket reply = new DatagramPacket(inBuf, inBuf.length);
            client.receive(reply);
            String got = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
            System.out.println("client got: '" + got + "' from " + reply.getSocketAddress());
        }

        server.join();
    }

    static void runServer() {
        try (DatagramSocket server = new DatagramSocket(SERVER_PORT)) {
            server.setSoTimeout(3000);
            byte[] buf = new byte[1024];
            DatagramPacket request = new DatagramPacket(buf, buf.length);
            server.receive(request);
            String msg = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
            System.out.println("server got: '" + msg + "' from " + request.getSocketAddress());

            byte[] reply = "pong".getBytes(StandardCharsets.UTF_8);
            server.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
