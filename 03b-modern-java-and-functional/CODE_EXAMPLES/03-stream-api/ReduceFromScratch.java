import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ReduceFromScratch {

    public static void main(String[] args) {
        List<Integer> ns = List.of(3, 1, 4, 1, 5, 9, 2, 6);

        // 2-arg reduce: identity + binary op. Always returns a value.
        int sum = ns.stream().reduce(0, Integer::sum);
        System.out.println("sum:      " + sum);

        // 1-arg reduce: no identity. Returns Optional<T> -- empty for an empty stream.
        Optional<Integer> max = ns.stream().reduce(Integer::max);
        System.out.println("max:      " + max.orElseThrow());

        // Building a string by reduce -- works but Collectors.joining is cleaner.
        String concat = Stream.of("a", "b", "c").reduce("", (a, b) -> a + b);
        System.out.println("concat:   " + concat);

        // 3-arg reduce: identity, accumulator, combiner. Combiner needed for parallel
        // streams when the type of accumulated value differs from element type.
        int totalLength = Stream.of("alpha", "beta", "gamma")
            .reduce(0, (acc, s) -> acc + s.length(), Integer::sum);
        System.out.println("total len:" + totalLength);

        // For sum of int over objects, prefer mapToInt + sum -- no boxing.
        int totalLen2 = Stream.of("alpha", "beta", "gamma").mapToInt(String::length).sum();
        System.out.println("total len (mapToInt): " + totalLen2);
    }
}
