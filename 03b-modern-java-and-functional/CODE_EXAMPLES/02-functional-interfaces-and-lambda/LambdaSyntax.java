import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

public class LambdaSyntax {

    public static void main(String[] args) {
        // 1) Zero parameters.
        Supplier<Integer> answer = () -> 42;
        System.out.println("answer:   " + answer.get());

        // 2) One parameter, type inferred, no parens.
        Function<Integer, Integer> doubler = x -> x * 2;
        System.out.println("doubler:  " + doubler.apply(21));

        // 3) Multiple parameters -- parens required.
        BiFunction<Integer, Integer, Integer> sum = (x, y) -> x + y;
        System.out.println("sum:      " + sum.apply(3, 4));

        // 4) Explicit parameter types (rarely needed, but legal).
        IntBinaryOperator product = (int x, int y) -> x * y;
        System.out.println("product:  " + product.applyAsInt(6, 7));

        // 5) Block body with explicit return.
        Function<Integer, Integer> sumTo = x -> {
            int s = 0;
            for (int i = 1; i <= x; i++) s += i;
            return s;
        };
        System.out.println("sumTo:    " + sumTo.apply(10));
    }
}
