import java.util.HashMap;

public class LoadFactorAndResize {

    public static void main(String[] args) {
        // start with capacity 16, load factor 0.75 -> resize triggers at size 13
        HashMap<Integer, Integer> map = new HashMap<>(16, 0.75f);

        for (int i = 1; i <= 100; i++) {
            map.put(i, i * 10);
            if (i == 12 || i == 13 || i == 24 || i == 25 || i == 49 || i == 50 || i == 100) {
                System.out.println("after put #" + i + ", size=" + map.size());
            }
        }

        System.out.println();
        System.out.println("Notable resize triggers:");
        System.out.println("  size 13 -> capacity grows 16  -> 32   (next resize at ~24)");
        System.out.println("  size 25 -> capacity grows 32  -> 64   (next resize at ~48)");
        System.out.println("  size 49 -> capacity grows 64  -> 128  (next resize at ~96)");
        System.out.println("  size 97 -> capacity grows 128 -> 256");
        System.out.println();
        System.out.println("Each resize is an O(n) rehash. Avoid by pre-sizing:");
        System.out.println("  new HashMap<>((int)(expectedSize / 0.75) + 1)");
    }
}
