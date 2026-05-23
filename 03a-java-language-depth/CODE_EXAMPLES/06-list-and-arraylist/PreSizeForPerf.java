import java.util.ArrayList;

public class PreSizeForPerf {

    public static void main(String[] args) {
        int N = 1_000_000;

        long start = System.nanoTime();
        ArrayList<Integer> defaultSized = new ArrayList<>();
        for (int i = 0; i < N; i++) defaultSized.add(i);
        long elapsedMsDefault = (System.nanoTime() - start) / 1_000_000;
        System.out.println("default capacity, " + N + " adds: " + elapsedMsDefault + " ms");

        start = System.nanoTime();
        ArrayList<Integer> preSized = new ArrayList<>(N);
        for (int i = 0; i < N; i++) preSized.add(i);
        long elapsedMsPreSized = (System.nanoTime() - start) / 1_000_000;
        System.out.println("pre-sized for " + N + ", same adds: " + elapsedMsPreSized + " ms");

        System.out.println();
        System.out.println("pre-sizing skips ~25 array copies during growth.");
        System.out.println("the savings get larger as N grows.");
    }
}
