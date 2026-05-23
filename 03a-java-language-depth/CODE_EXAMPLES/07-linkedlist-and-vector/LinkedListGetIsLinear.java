import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LinkedListGetIsLinear {

    public static void main(String[] args) {
        int N = 50_000;

        List<Integer> linked = new LinkedList<>();
        List<Integer> array  = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            linked.add(i);
            array.add(i);
        }

        for (int idx : new int[] {100, 10_000, 25_000, 49_999}) {
            time("LinkedList.get(" + idx + ")", linked, idx);
        }

        System.out.println();

        for (int idx : new int[] {100, 10_000, 25_000, 49_999}) {
            time("ArrayList.get(" + idx + ") ", array, idx);
        }

        System.out.println();
        System.out.println("LinkedList.get(i) walks from the nearer end -- far indices cost more.");
        System.out.println("ArrayList.get(i) is direct array access -- constant.");
    }

    static void time(String label, List<Integer> list, int idx) {
        int iterations = 5_000;
        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += list.get(idx);
        }
        long elapsedUs = (System.nanoTime() - start) / 1_000;
        System.out.println(label + " -- " + iterations + " calls in " + elapsedUs + " us (sum=" + sum + ")");
    }
}
