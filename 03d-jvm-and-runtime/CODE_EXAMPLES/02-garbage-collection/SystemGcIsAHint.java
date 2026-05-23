import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class SystemGcIsAHint {

    public static void main(String[] args) {
        Runtime r = Runtime.getRuntime();

        // Allocate ~100 MB of unreferenced garbage.
        for (int i = 0; i < 100; i++) {
            byte[] tmp = new byte[1024 * 1024];
            tmp[0] = (byte) i;
        }

        long beforeMb = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
        long beforeGcs = totalGcCount();

        // Hint to the JVM. It MAY perform a GC. It may also ignore us.
        System.gc();

        long afterMb = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
        long afterGcs = totalGcCount();

        System.out.println("heap used before System.gc(): " + beforeMb + " MB");
        System.out.println("heap used after  System.gc(): " + afterMb + " MB");
        System.out.println("GC count delta: " + (afterGcs - beforeGcs));
        System.out.println();
        System.out.println("System.gc() is a hint, not a command.");
        System.out.println("Calling it in production can pin a long full-GC pause at the worst moment.");
    }

    static long totalGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) sum += gc.getCollectionCount();
        return sum;
    }
}
