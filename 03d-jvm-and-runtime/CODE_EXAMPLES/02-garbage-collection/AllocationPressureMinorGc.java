import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class AllocationPressureMinorGc {

    public static void main(String[] args) {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        printGcs("before:", gcs);

        // Allocate a lot of short-lived objects. Each iteration the byte[] becomes unreachable.
        long allocated = 0;
        for (int i = 0; i < 200_000; i++) {
            byte[] tmp = new byte[10 * 1024];   // 10 KB
            tmp[0] = (byte) i;
            allocated += tmp.length;
        }

        printGcs("after " + (allocated / (1024L * 1024)) + " MB allocated:", gcs);
        System.out.println();
        System.out.println("Run with -Xlog:gc to also see each minor GC printed by the JVM.");
        System.out.println("active collector(s): " + collectorNames());
    }

    static void printGcs(String label, List<GarbageCollectorMXBean> gcs) {
        System.out.println(label);
        for (GarbageCollectorMXBean gc : gcs) {
            System.out.println("  " + gc.getName() + ": collections=" + gc.getCollectionCount()
                + ", totalTime=" + gc.getCollectionTime() + "ms");
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
