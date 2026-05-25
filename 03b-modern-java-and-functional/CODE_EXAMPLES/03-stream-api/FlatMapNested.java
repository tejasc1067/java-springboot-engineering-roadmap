import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlatMapNested {

    record Order(String id, List<String> items) {}

    public static void main(String[] args) {
        List<Order> orders = List.of(
            new Order("o-1", List.of("apple", "bread")),
            new Order("o-2", List.of("cheese")),
            new Order("o-3", List.of("apple", "donut", "egg"))
        );

        // map: turns each Order into a List<String> -> we get Stream<List<String>>
        // (awkward; have to iterate the inner lists separately)
        List<List<String>> nested = orders.stream()
            .map(Order::items)
            .collect(Collectors.toList());
        System.out.println("with map:     " + nested);

        // flatMap: each Order becomes a stream of items; all merged into one flat stream.
        List<String> flat = orders.stream()
            .flatMap(o -> o.items().stream())
            .collect(Collectors.toList());
        System.out.println("with flatMap: " + flat);

        // Distinct items (common follow-up).
        List<String> distinctItems = orders.stream()
            .flatMap(o -> o.items().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        System.out.println("distinct:     " + distinctItems);

        // flatMap also flattens streams of streams from any source.
        List<Integer> merged = Stream.of(Stream.of(1, 2), Stream.of(3, 4, 5))
            .flatMap(s -> s)
            .collect(Collectors.toList());
        System.out.println("merged:       " + merged);
    }
}
