// Streams: a pipeline of operations on a sequence of values. Start from a
// source (collection / stream / generator), chain intermediate operations
// (filter, map, sorted), end with a terminal one (toList, count, sum, ...).

import java.util.List;
import java.util.stream.Collectors;

public class StreamsBasics {
    public static void main(String[] args) {

        List<String> names = List.of("alice", "bob", "carol", "dave", "eve");

        // filter + map + sorted + toList — the everyday pipeline
        List<String> upper = names.stream()
                .filter(n -> n.length() > 3)
                .map(String::toUpperCase)
                .sorted()
                .toList();
        System.out.println("uppercased names > 3 chars: " + upper);

        // count
        long short_ = names.stream().filter(n -> n.length() <= 3).count();
        System.out.println("short names: " + short_);

        // sum (numeric stream)
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        System.out.println("sum 1..10: " + sum);

        // sum of squares of even numbers
        int squares = numbers.stream()
                .filter(n -> n % 2 == 0)
                .mapToInt(n -> n * n)
                .sum();
        System.out.println("sum of squares of evens: " + squares);

        // group by first letter
        var byInitial = names.stream()
                .collect(Collectors.groupingBy(n -> n.charAt(0)));
        System.out.println("grouped by initial: " + byInitial);
    }
}
