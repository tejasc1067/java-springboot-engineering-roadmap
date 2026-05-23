import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObservingGcWithBeans {

    public static void main(String[] args) throws InterruptedException {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        Map<String, Long> previousCounts = new HashMap<>();
        for (GarbageCollectorMXBean gc : gcs) previousCounts.put(gc.getName(), 0L);

        for (int second = 0; second < 5; second++) {
            // Generate garbage at a steady rate.
            for (int i = 0; i < 20_000; i++) {
                byte[] tmp = new byte[5 * 1024];
                tmp[0] = 1;
            }

            for (GarbageCollectorMXBean gc : gcs) {
                long total = gc.getCollectionCount();
                long delta = total - previousCounts.get(gc.getName());
                previousCounts.put(gc.getName(), total);
                if (delta > 0) {
                    System.out.println("t=" + second + "s  " + gc.getName() + " +" + delta + " collections (total " + total + ", " + gc.getCollectionTime() + "ms)");
                }
            }
            Thread.sleep(200);
        }
    }
}
