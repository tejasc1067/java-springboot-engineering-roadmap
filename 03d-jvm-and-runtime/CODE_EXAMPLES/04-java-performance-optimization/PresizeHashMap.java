import java.util.HashMap;
import java.util.Map;

public class PresizeHashMap {

    public static void main(String[] args) {
        int n = 1_000_000;

        // 1) Default capacity (16). Resizes (rehashes everything) many times as it grows.
        long t1 = System.nanoTime();
        Map<Integer, Integer> growing = new HashMap<>();
        for (int i = 0; i < n; i++) growing.put(i, i);
        long growMs = (System.nanoTime() - t1) / 1_000_000;

        // 2) Pre-sized so it never has to resize.
        // load factor 0.75 -> initial capacity = expected / 0.75 + 1
        int initial = (int) (n / 0.75f) + 1;
        long t2 = System.nanoTime();
        Map<Integer, Integer> presized = new HashMap<>(initial);
        for (int i = 0; i < n; i++) presized.put(i, i);
        long preMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("default capacity: " + growMs + " ms");
        System.out.println("pre-sized:        " + preMs  + " ms");
        System.out.println();
        System.out.println("Each resize copies every existing entry into a larger table.");
        System.out.println("Pre-sizing skips that work entirely.");
    }
}
