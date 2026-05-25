import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelStreamHazard {

    public static void main(String[] args) {
        int n = 100_000;

        // BROKEN: many parallel workers mutating the same ArrayList.
        // ArrayList is not thread-safe -- forEach + add races. Result size varies, sometimes throws.
        List<Integer> broken = new ArrayList<>();
        try {
            IntStream.range(0, n).parallel().forEach(broken::add);
        } catch (Throwable t) {
            System.out.println("broken threw: " + t.getClass().getSimpleName());
        }
        System.out.println("expected size: " + n + "  broken size: " + broken.size());

        // FIXED: use a Collector. The framework knows how to combine partial results safely.
        List<Integer> safe = IntStream.range(0, n).parallel()
            .boxed()
            .collect(Collectors.toList());
        System.out.println("expected size: " + n + "  safe   size: " + safe.size());

        System.out.println();
        System.out.println("Lesson: never side-effect into a shared mutable collection from a parallel stream.");
        System.out.println("Use a Collector. It produces per-thread containers and merges them at the end.");
    }
}
