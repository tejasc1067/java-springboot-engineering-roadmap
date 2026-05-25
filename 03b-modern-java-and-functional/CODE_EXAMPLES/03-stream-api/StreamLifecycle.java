import java.util.Optional;
import java.util.stream.Stream;

public class StreamLifecycle {

    public static void main(String[] args) {
        System.out.println("=== findFirst (short-circuits) ===");
        Optional<Integer> firstEvenTimes10 = Stream.of(1, 2, 3, 4, 5)
            .peek(n -> System.out.println("filter sees:  " + n))
            .filter(n -> n % 2 == 0)
            .peek(n -> System.out.println("map sees:     " + n))
            .map(n -> n * 10)
            .findFirst();
        System.out.println("result: " + firstEvenTimes10);

        System.out.println();
        System.out.println("Notice: only ONE element reached `map` and the pipeline stopped early.");
        System.out.println("Streams are LAZY -- intermediate ops fire per element, not per operator.");

        System.out.println();
        System.out.println("=== count (no short-circuit -- visits all) ===");
        long total = Stream.of(1, 2, 3, 4, 5)
            .peek(n -> System.out.println("filter sees: " + n))
            .filter(n -> n % 2 == 0)
            .count();
        System.out.println("count: " + total);
    }
}
