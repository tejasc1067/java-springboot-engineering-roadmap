import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ChannelExample {

    public static void main(String[] args)
            throws Exception {

        RandomAccessFile file =
                new RandomAccessFile(
                        "sample.txt",
                        "rw"
                );

        FileChannel channel =
                file.getChannel();

        ByteBuffer buffer =
                ByteBuffer.allocate(1024);

        buffer.put(
                "Java NIO Channel".getBytes()
        );

        buffer.flip();

        channel.write(buffer);

        channel.close();

        file.close();

        System.out.println(
                "Data Written"
        );
    }
}