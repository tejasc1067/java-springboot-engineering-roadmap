import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapAsLruCache {

    static class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        LruCache(int maxSize) {
            super(16, 0.75f, true);     // accessOrder = true → most-recently-accessed moves to tail
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    public static void main(String[] args) {
        LruCache<String, Integer> cache = new LruCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        System.out.println("after putting A,B,C:           " + cache);

        cache.get("A");
        System.out.println("after get(A) (touches A):     " + cache);

        cache.put("D", 4);
        System.out.println("after put(D) (evicts eldest): " + cache + "   ← B was eldest, dropped");
    }
}
