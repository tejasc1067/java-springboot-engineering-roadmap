// A lambda is a short syntax for a one-method function. Java picks the
// concrete interface based on the variable type (Runnable, Function, ...).

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class Lambdas {
    public static void main(String[] args) {

        // Runnable: () -> void
        Runnable greet = () -> System.out.println("hello");
        greet.run();

        // Function<T,R>: T -> R
        Function<Integer, Integer> doubler = n -> n * 2;
        System.out.println("double(7) = " + doubler.apply(7));

        // Predicate<T>: T -> boolean
        Predicate<String> isLong = s -> s.length() > 4;
        System.out.println("isLong(\"hi\")    = " + isLong.test("hi"));
        System.out.println("isLong(\"hello\") = " + isLong.test("hello"));

        // BiFunction<T,U,R>: (T, U) -> R
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        System.out.println("add(3, 4) = " + add.apply(3, 4));

        // Multi-line lambda: use braces and an explicit return (if non-void).
        Function<Integer, String> describe = n -> {
            if (n < 0) return "negative";
            if (n == 0) return "zero";
            return "positive";
        };
        System.out.println("describe(-3) = " + describe.apply(-3));
        System.out.println("describe(7)  = " + describe.apply(7));

        // For contrast — the same Runnable, anonymous-class form.
        Runnable verbose = new Runnable() {
            @Override public void run() {
                System.out.println("\n(anonymous class equivalent)");
            }
        };
        verbose.run();
    }
}
