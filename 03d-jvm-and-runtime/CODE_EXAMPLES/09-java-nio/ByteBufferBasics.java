import java.nio.ByteBuffer;

public class ByteBufferBasics {

    public static void main(String[] args) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        print("fresh allocate(8)", buf);

        buf.put((byte) 0x41);   // 'A'
        buf.put((byte) 0x42);   // 'B'
        buf.put((byte) 0x43);   // 'C'
        print("after 3 puts",     buf);

        buf.flip();             // switch from writing to reading: limit=pos, pos=0
        print("after flip()",     buf);

        byte b1 = buf.get();
        byte b2 = buf.get();
        print("after 2 gets",     buf);
        System.out.println("got: '" + (char) b1 + "' '" + (char) b2 + "'");

        buf.clear();            // reset cursors -- old bytes still in memory but invisible
        print("after clear()",    buf);

        buf.put((byte) 0x58);   // 'X' overwrites the first slot
        print("after put X",      buf);

        System.out.println();
        System.out.println("Lesson: flip() between writing and reading is mandatory.");
        System.out.println("clear() resets cursors but does NOT erase the underlying bytes.");
    }

    static void print(String label, ByteBuffer b) {
        System.out.printf("%-22s position=%d, limit=%d, capacity=%d%n",
            label, b.position(), b.limit(), b.capacity());
    }
}
