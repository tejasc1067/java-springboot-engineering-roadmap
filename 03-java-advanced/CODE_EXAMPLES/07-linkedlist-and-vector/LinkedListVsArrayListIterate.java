import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LinkedListVsArrayListIterate {

    public static void main(String[] args) {
        int N = 5_000_000;

        List<Integer> array = new ArrayList<>(N);
        List<Integer> linked = new LinkedList<>();
        for (int i = 0; i < N; i++) {
            array.add(i);
            linked.add(i);
        }

        long start = System.nanoTime();
        long sum = 0;
        for (int v : array) sum += v;
        long elapsedMsArray = (System.nanoTime() - start) / 1_000_000;
        System.out.println("ArrayList iterate sum:  " + sum + " in " + elapsedMsArray + " ms");

        start = System.nanoTime();
        sum = 0;
        for (int v : linked) sum += v;
        long elapsedMsLinked = (System.nanoTime() - start) / 1_000_000;
        System.out.println("LinkedList iterate sum: " + sum + " in " + elapsedMsLinked + " ms");

        System.out.println();
        System.out.println("Same loop body, same N -- LinkedList loses on cache locality.");
        System.out.println("Each next() chases a pointer to a heap-scattered node.");
    }
}
