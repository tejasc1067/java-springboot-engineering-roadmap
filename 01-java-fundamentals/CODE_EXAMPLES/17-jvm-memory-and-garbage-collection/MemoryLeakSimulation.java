// A classic "leak in a GC language": a static collection that grows forever.
// The GC can't reclaim its contents because the static field always reaches
// them.
//
// Run with a small heap to crash faster:
//   java -Xmx64m MemoryLeakSimulation.java
//
// You'll see heap usage climb until OutOfMemoryError. The fix is to set a
// size limit or use a cache library with eviction (e.g., Caffeine).

import java.util.ArrayList;
import java.util.List;

public class MemoryLeakSimulation {

    // Static — anchors the list for the entire program lifetime.
    static final List<byte[]> bigStuff = new ArrayList<>();

    public static void main(String[] args) {

        long t0 = System.currentTimeMillis();
        int chunks = 0;

        try {
            while (true) {
                bigStuff.add(new byte[1_000_000]);   // 1 MB
                chunks++;
                if (chunks % 50 == 0) {
                    long heap = Runtime.getRuntime().totalMemory() / 1_000_000;
                    long used = (Runtime.getRuntime().totalMemory()
                            - Runtime.getRuntime().freeMemory()) / 1_000_000;
                    System.out.println("chunks: " + chunks
                            + "   used: " + used + " MB"
                            + "   heap: " + heap + " MB");
                }
            }
        } catch (OutOfMemoryError oom) {
            // Free the list FIRST so the heap has room for any allocation
            // we do during reporting (println creates temporary Strings).
            bigStuff.clear();
            System.gc();
            long elapsed = System.currentTimeMillis() - t0;
            System.out.println("\nOOM after " + chunks + " chunks ("
                    + elapsed + " ms). This is the leak.");
        }
    }
}
