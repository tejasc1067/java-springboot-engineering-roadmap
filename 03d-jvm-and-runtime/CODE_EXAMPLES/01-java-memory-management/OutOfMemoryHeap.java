import java.util.ArrayList;
import java.util.List;

public class OutOfMemoryHeap {

    public static void main(String[] args) {
        List<byte[]> sink = new ArrayList<>();
        int allocations = 0;
        try {
            while (true) {
                sink.add(new byte[1024 * 1024]);   // 1 MB per loop, never released
                allocations++;
            }
        } catch (OutOfMemoryError e) {
            System.out.println("allocated " + allocations + " MB before OOM");
            System.out.println("error message: " + e.getMessage());
            System.out.println();
            System.out.println("The specific message ('Java heap space', 'GC overhead limit exceeded', etc.)");
            System.out.println("tells you which failure mode hit. See topic 01 markdown for the table.");
            // Drop the sink so we don't OOM again while printing
            sink = null;
        }
    }
}
