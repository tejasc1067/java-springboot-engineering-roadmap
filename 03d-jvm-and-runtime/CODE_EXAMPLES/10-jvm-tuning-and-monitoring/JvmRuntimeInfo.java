import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class JvmRuntimeInfo {

    public static void main(String[] args) {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Runtime r = Runtime.getRuntime();

        System.out.println("Java version:    " + System.getProperty("java.version"));
        System.out.println("VM name:         " + rt.getVmName());
        System.out.println("PID:             " + rt.getPid());
        System.out.println("Uptime ms:       " + rt.getUptime());

        System.out.println();
        System.out.println("CPUs reported:   " + r.availableProcessors());
        System.out.println("Heap max:        " + (r.maxMemory()   / (1024 * 1024)) + " MB");
        System.out.println("Heap total:      " + (r.totalMemory() / (1024 * 1024)) + " MB");
        System.out.println("Heap free:       " + (r.freeMemory()  / (1024 * 1024)) + " MB");

        System.out.println();
        System.out.println("Garbage collectors:");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName() + " (collections=" + gc.getCollectionCount()
                + ", time=" + gc.getCollectionTime() + "ms)");
        }

        System.out.println();
        System.out.println("Some JVM input args:");
        for (String arg : rt.getInputArguments()) {
            System.out.println("  " + arg);
        }
    }
}
