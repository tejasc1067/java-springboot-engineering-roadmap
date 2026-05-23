import java.util.ArrayList;
import java.util.List;

public class GarbageVsLeak {

    static final List<byte[]> leaked = new ArrayList<>();   // GC root -> pins everything inside

    public static void main(String[] args) {
        Runtime r = Runtime.getRuntime();

        System.out.println("=== garbage (reachable then unreachable) ===");
        for (int i = 0; i < 5; i++) {
            allocateAndDrop();              // each call allocates 50MB, then lets it go out of scope
            r.gc();
            long used = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
            System.out.println("after round " + i + ": heap used = " + used + " MB");
        }

        System.out.println();
        System.out.println("=== leak (always reachable) ===");
        for (int i = 0; i < 5; i++) {
            leaked.add(new byte[50 * 1024 * 1024]);   // stored in a static collection -> pinned
            r.gc();
            long used = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
            System.out.println("after round " + i + ": heap used = " + used + " MB");
        }
    }

    static void allocateAndDrop() {
        byte[] tmp = new byte[50 * 1024 * 1024];
        tmp[0] = 1;    // ensure not optimized away
        // tmp goes out of scope when this method returns; GC can reclaim it
    }
}
