import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class HashCollisionDegrades {

    static class BadHash {
        final int id;
        BadHash(int id) { this.id = id; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BadHash other)) return false;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static class GoodHash {
        final int id;
        GoodHash(int id) { this.id = id; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GoodHash other)) return false;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public static void main(String[] args) {
        int N = 50_000;

        long start = System.nanoTime();
        Set<BadHash> bad = new HashSet<>();
        for (int i = 0; i < N; i++) bad.add(new BadHash(i));
        long elapsedMsBad = (System.nanoTime() - start) / 1_000_000;
        System.out.println("BadHash  insert " + N + " elements: " + elapsedMsBad + " ms");

        start = System.nanoTime();
        Set<GoodHash> good = new HashSet<>();
        for (int i = 0; i < N; i++) good.add(new GoodHash(i));
        long elapsedMsGood = (System.nanoTime() - start) / 1_000_000;
        System.out.println("GoodHash insert " + N + " elements: " + elapsedMsGood + " ms");

        System.out.println();
        System.out.println("BadHash funnels every element into bucket 0.");
        System.out.println("Java 8+ treeifies long bucket chains, so it's O(log n) instead of O(n)");
        System.out.println("-- but still much slower than the constant-time GoodHash case.");
    }
}
