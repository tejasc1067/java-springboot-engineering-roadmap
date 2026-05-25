import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FiveCoreInterfaces {

    public static void main(String[] args) {
        // Predicate<T> -- "is this true of T?"
        Predicate<String> isLong = s -> s.length() > 5;
        System.out.println("isLong(\"hello\"):  " + isLong.test("hello"));
        System.out.println("isLong(\"goodbye\"):" + isLong.test("goodbye"));

        // Function<T, R> -- "turn T into R"
        Function<String, Integer> length = String::length;
        System.out.println("length(\"hello\"):  " + length.apply("hello"));

        // Consumer<T> -- "do something with T, return void"
        Consumer<String> log = s -> System.out.println("[log] " + s);
        log.accept("first event");
        log.accept("second event");

        // Supplier<T> -- "give me a T (no inputs)"
        Supplier<String> nowIso = () -> Instant.now().toString();
        System.out.println("nowIso(): " + nowIso.get());

        // BiFunction<T, U, R> -- "combine T and U into R"
        BiFunction<Integer, Integer, Integer> add = Integer::sum;
        System.out.println("add(3, 4): " + add.apply(3, 4));

        // Realistic combined use: count adult names.
        List<String> names = List.of("Al", "Bobby", "Carol", "Dave");
        long adults = names.stream().filter(isLong).count();
        System.out.println("names with len>5: " + adults);
    }
}
