import java.util.HashMap;
import java.util.Map;

public class HashMapPutWalkthrough {

    static int bucketOf(Object key, int capacity) {
        int h = key.hashCode();
        h = h ^ (h >>> 16);
        return (capacity - 1) & h;
    }

    public static void main(String[] args) {
        Map<String, Integer> map = new HashMap<>();
        int capacity = 16;

        String[] keys = {"Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace"};
        for (String k : keys) {
            int bucket = bucketOf(k, capacity);
            map.put(k, k.length());
            System.out.printf("put(\"%s\") -- hash=0x%08x -> bucket %d (of %d)%n",
                              k, k.hashCode(), bucket, capacity);
        }

        System.out.println();
        System.out.println("get(\"Bob\"):");
        System.out.println("  compute bucket = " + bucketOf("Bob", capacity));
        System.out.println("  walk that bucket's chain, equals-compare each entry");
        System.out.println("  found: " + map.get("Bob"));
    }
}
