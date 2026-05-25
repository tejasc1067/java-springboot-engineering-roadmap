import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PrimitiveStreams {

    public static void main(String[] args) {
        // IntStream.range / rangeClosed -- common starting point.
        int sumTo100 = IntStream.rangeClosed(1, 100).sum();
        System.out.println("sum 1..100:    " + sumTo100);

        // average() returns OptionalDouble because the stream might be empty.
        OptionalDouble avg = IntStream.of(1, 2, 3, 4, 5).average();
        System.out.println("average:       " + avg.orElseThrow());

        // Object stream -> IntStream via mapToInt (no boxing for the sum).
        int totalLen = Stream.of("alpha", "beta", "gamma")
            .mapToInt(String::length)
            .sum();
        System.out.println("total length:  " + totalLen);

        // IntStream -> object stream via mapToObj.
        String letters = IntStream.rangeClosed('A', 'E')
            .mapToObj(c -> String.valueOf((char) c))
            .reduce("", (a, b) -> a + b);
        System.out.println("letters A..E:  " + letters);

        // Summary stats -- min, max, sum, count, average in one pass.
        var stats = IntStream.of(7, 3, 9, 1, 5).summaryStatistics();
        System.out.println("stats:         " + stats);
    }
}
