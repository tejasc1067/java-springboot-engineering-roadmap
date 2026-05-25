import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GcCountAndTime {

    public static void main(String[] args) {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        Map<String, long[]> before = snapshot(gcs);

        // Generate ~200 MB of short-lived garbage.
        long acc = 0;
        for (int i = 0; i < 200_000; i++) {
            byte[] tmp = new byte[1024];
            tmp[0] = (byte) i;
            acc += tmp.length;
        }
        if (acc < 0) System.out.println("");   // prevent dead-code elim

        Map<String, long[]> after = snapshot(gcs);

        System.out.printf("%-25s %12s %12s%n", "collector", "+collections", "+time(ms)");
        for (GarbageCollectorMXBean gc : gcs) {
            long[] b = before.get(gc.getName());
            long[] a = after.get(gc.getName());
            System.out.printf("%-25s %12d %12d%n",
                gc.getName(),
                a[0] - b[0],
                a[1] - b[1]);
        }
    }

    static Map<String, long[]> snapshot(List<GarbageCollectorMXBean> gcs) {
        Map<String, long[]> m = new HashMap<>();
        for (GarbageCollectorMXBean gc : gcs) {
            m.put(gc.getName(), new long[] { gc.getCollectionCount(), gc.getCollectionTime() });
        }
        return m;
    }
}
