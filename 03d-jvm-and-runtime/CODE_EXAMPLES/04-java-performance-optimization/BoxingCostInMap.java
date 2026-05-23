import java.util.HashMap;
import java.util.Map;

public class BoxingCostInMap {

    public static void main(String[] args) {
        int n = 5_000_000;

        // 1) Map<Long, Long> -- every put boxes both key and value.
        long t1 = System.nanoTime();
        Map<Long, Long> boxed = new HashMap<>(n * 2);
        long sumBoxed = 0;
        for (long i = 0; i < n; i++) {
            boxed.put(i, i);                       // 2 wrapper allocations per call
            sumBoxed += boxed.get(i);              // unboxing per get
        }
        long boxedMs = (System.nanoTime() - t1) / 1_000_000;

        // 2) long[] -- no boxing, contiguous memory.
        long t2 = System.nanoTime();
        long[] array = new long[n];
        long sumArr = 0;
        for (int i = 0; i < n; i++) {
            array[i] = i;
            sumArr += array[i];
        }
        long arrMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("Map<Long,Long>: " + boxedMs + " ms  (sum=" + sumBoxed + ")");
        System.out.println("long[]:         " + arrMs   + " ms  (sum=" + sumArr   + ")");
        System.out.println();
        System.out.println("Boxing allocates ~24 bytes per Long. The array uses 8 bytes per slot, with no allocations during the inner loop.");
    }
}
