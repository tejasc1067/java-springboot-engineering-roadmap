import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;

public class HumongousAllocation {

    public static void main(String[] args) {
        printMemoryPools("before:");

        // A G1 region is typically 1-32MB. An allocation >50% of region size is
        // "humongous" and bypasses the young generation -- straight to Old.
        // 32MB is humongous for any default-region-size G1.
        List<byte[]> retained = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            retained.add(new byte[32 * 1024 * 1024]);
        }

        // Some small allocations to push minor GCs.
        for (int i = 0; i < 50_000; i++) {
            byte[] tmp = new byte[8 * 1024];
            tmp[0] = (byte) i;
        }

        printMemoryPools("after 4 x 32MB + small churn:");
        System.out.println("collector(s): " + collectorNames());
        System.out.println();
        System.out.println("Notice Old generation usage. Large allocations sidestepped the young gen.");
    }

    static void printMemoryPools(String label) {
        System.out.println(label);
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().toLowerCase().contains("eden") || pool.getName().toLowerCase().contains("old")
                || pool.getName().toLowerCase().contains("tenured")) {
                System.out.println("  " + pool.getName() + ": used=" + (pool.getUsage().getUsed() / (1024 * 1024)) + " MB");
            }
        }
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
