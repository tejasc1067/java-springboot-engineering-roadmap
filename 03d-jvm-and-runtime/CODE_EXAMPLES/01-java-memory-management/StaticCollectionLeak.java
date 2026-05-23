import java.util.HashMap;
import java.util.Map;

public class StaticCollectionLeak {

    // The cache is a GC root. Every entry is pinned alive until explicitly removed.
    static final Map<Integer, byte[]> cache = new HashMap<>();

    public static void main(String[] args) {
        Runtime r = Runtime.getRuntime();
        for (int i = 0; ; i++) {
            cache.put(i, new byte[100 * 1024]);   // 100 KB each
            if (i % 50 == 0) {
                long used = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
                long max = r.maxMemory() / (1024 * 1024);
                System.out.println("entries=" + cache.size() + ", heap used=" + used + "MB / max=" + max + "MB");
            }
            if (i > 100_000) break;   // safety stop
        }
    }
}
