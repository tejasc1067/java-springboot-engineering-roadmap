import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class MeasureGcPause {

    public static void main(String[] args) {
        long before = totalGcTimeMs();
        long iterations = 0;

        long start = System.nanoTime();
        long deadline = start + 3_000_000_000L;   // run for ~3 seconds
        List<byte[]> retained = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            // Mix of short-lived and longer-lived allocations to stress both generations.
            byte[] shortLived = new byte[20 * 1024];
            shortLived[0] = 1;
            if ((iterations & 0xFF) == 0) {
                retained.add(new byte[100 * 1024]);
                if (retained.size() > 200) retained.remove(0);
            }
            iterations++;
        }

        long after = totalGcTimeMs();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("iterations: " + iterations);
        System.out.println("elapsed:    " + elapsedMs + " ms");
        System.out.println("total GC time during the run: " + (after - before) + " ms");
        System.out.println("collector(s): " + collectorNames());
        System.out.println();
        System.out.println("Try running with -XX:+UseParallelGC vs -XX:+UseG1GC -- the GC time differs.");
    }

    static long totalGcTimeMs() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) sum += gc.getCollectionTime();
        return sum;
    }

    static String collectorNames() {
        StringBuilder sb = new StringBuilder();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(gc.getName());
        }
        return sb.toString();
    }
}
