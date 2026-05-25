import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class BottleneckSnapshot {

    public static void main(String[] args) throws InterruptedException {
        // Start one busy thread and one parked thread so the snapshot has signal.
        Thread busy = new Thread(() -> { long s = 0; for (long i = 0; i < Long.MAX_VALUE; i++) s += i; }, "busy");
        busy.setDaemon(true);
        busy.start();

        Thread parked = new Thread(() -> {
            try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "parked");
        parked.setDaemon(true);
        parked.start();

        Thread.sleep(200);  // let them run

        OperatingSystemMXBean os    = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean mem            = ManagementFactory.getMemoryMXBean();
        ThreadMXBean th             = ManagementFactory.getThreadMXBean();
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("=== bottleneck snapshot ===");
        System.out.println("processors:     " + os.getAvailableProcessors());
        System.out.println("system loadavg: " + os.getSystemLoadAverage()
            + "    (-1 means platform doesn't expose it -- Windows)");
        System.out.println("heap used:      " + (mem.getHeapMemoryUsage().getUsed() / (1024 * 1024)) + " MB");
        System.out.println("heap max:       " + (mem.getHeapMemoryUsage().getMax()  / (1024 * 1024)) + " MB");
        System.out.println("live threads:   " + th.getThreadCount());
        System.out.println("daemon threads: " + th.getDaemonThreadCount());
        System.out.println("peak threads:   " + th.getPeakThreadCount());
        long[] deadlocks = th.findDeadlockedThreads();
        System.out.println("deadlocks:      " + (deadlocks == null ? "none" : deadlocks.length));
        for (GarbageCollectorMXBean gc : gcs) {
            System.out.println("gc " + gc.getName() + ": " + gc.getCollectionCount() + " runs, " + gc.getCollectionTime() + " ms total");
        }

        System.out.println();
        System.out.println("This is the same data Micrometer / Prometheus exporters publish.");
    }
}
