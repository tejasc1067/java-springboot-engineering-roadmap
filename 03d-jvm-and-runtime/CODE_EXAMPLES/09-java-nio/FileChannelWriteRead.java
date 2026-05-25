import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelWriteRead {

    public static void main(String[] args) throws IOException {
        Path tmp = Files.createTempFile("nio-channel-", ".bin");

        // Write a few records via FileChannel + ByteBuffer.
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.allocate(64);
            for (String msg : new String[] { "hello, ", "world!", "\n" }) {
                buf.clear();                            // reset to fill again
                buf.put(msg.getBytes(StandardCharsets.UTF_8));
                buf.flip();                              // switch to read mode for the channel
                while (buf.hasRemaining()) ch.write(buf);
            }
        }

        System.out.println("wrote " + Files.size(tmp) + " bytes to " + tmp);

        // Read it back.
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) ch.size());
            int total = 0;
            int n;
            while ((n = ch.read(buf)) > 0) total += n;
            buf.flip();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            System.out.println("read " + total + " bytes: " + new String(bytes, StandardCharsets.UTF_8).trim());
        }

        Files.deleteIfExists(tmp);
    }
}
