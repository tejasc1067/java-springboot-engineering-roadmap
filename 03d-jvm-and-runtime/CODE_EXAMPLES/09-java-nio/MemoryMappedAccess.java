import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MemoryMappedAccess {

    public static void main(String[] args) throws IOException {
        Path tmp = Files.createTempFile("mmap-", ".bin");
        long size = 1024L * 1024;   // 1 MB region

        // Make sure the file is at least `size` bytes -- mmap doesn't grow the file.
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            ch.position(size - 1).write(java.nio.ByteBuffer.wrap(new byte[] { 0 }));
        }

        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            // Memory-map the whole file into the JVM's address space.
            MappedByteBuffer mbb = ch.map(FileChannel.MapMode.READ_WRITE, 0, size);

            // Random-access writes via array-like syntax -- no per-call syscall.
            mbb.putInt(0,         0xCAFEBABE);
            mbb.putLong(8,        42L);
            mbb.putInt((int)size - 4, 0xDEADBEEF);

            // Force the mapped pages back to disk (optional; OS flushes lazily otherwise).
            mbb.force();

            // Read back.
            System.out.println("offset 0:        0x" + Integer.toHexString(mbb.getInt(0)));
            System.out.println("offset 8:        " + mbb.getLong(8));
            System.out.println("offset size-4:   0x" + Integer.toHexString(mbb.getInt((int)size - 4)));
        }

        System.out.println();
        System.out.println("Mapped buffers expose the file as a region of virtual memory.");
        System.out.println("No read()/write() syscall per access; the OS page cache backs it.");

        Files.deleteIfExists(tmp);
    }
}
