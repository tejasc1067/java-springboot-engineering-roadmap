import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class HeapUsageWatcher {

    public static void main(String[] args) throws InterruptedException {
        Runtime r = Runtime.getRuntime();
        long maxMb = r.maxMemory() / (1024 * 1024);
        System.out.println("max heap: " + maxMb + " MB");
        System.out.println("Try running with -Xmx128m to see GCs trigger sooner.");
        System.out.println();
        System.out.printf("%-7s %-12s %-12s %-12s%n", "t(ms)", "used(MB)", "gc-count", "gc-time(ms)");

        List<byte[]> garbage = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            // Allocate 4 MB, immediately throw away half of it -- typical "short-lived garbage" pattern.
            for (int j = 0; j < 4; j++) {
                byte[] chunk = new byte[1024 * 1024];
                chunk[0] = (byte) j;
                if (j % 2 == 0) garbage.add(chunk);   // keep half, drop half
            }
            long usedMb = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
            System.out.printf("%-7d %-12d %-12d %-12d%n",
                System.currentTimeMillis() - start,
                usedMb,
                totalGcCount(),
                totalGcTimeMs());
            Thread.sleep(100);
            if (garbage.size() > 16) garbage = new ArrayList<>(garbage.subList(8, garbage.size()));
        }
    }

    static long totalGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) sum += gc.getCollectionCount();
        return sum;
    }

    static long totalGcTimeMs() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) sum += gc.getCollectionTime();
        return sum;
    }
}
