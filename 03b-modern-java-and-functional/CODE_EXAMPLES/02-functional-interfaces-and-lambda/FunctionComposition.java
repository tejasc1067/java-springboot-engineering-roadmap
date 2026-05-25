import java.util.function.Function;
import java.util.function.Predicate;

public class FunctionComposition {

    public static void main(String[] args) {
        // Build small pieces.
        Function<String, String> trim  = String::trim;
        Function<String, String> upper = String::toUpperCase;

        // f.andThen(g) -- apply f, then g.
        Function<String, String> trimUpper = trim.andThen(upper);
        System.out.println("trim.andThen(upper): '" + trimUpper.apply("  hello  ") + "'");

        // f.compose(g) -- apply g first, then f (reverse order).
        Function<String, String> upperTrim = trim.compose(upper);
        System.out.println("trim.compose(upper): '" + upperTrim.apply("  hello  ") + "'");

        // Predicate composition.
        Predicate<String> notEmpty = s -> !s.isEmpty();
        Predicate<String> startsA  = s -> s.startsWith("A");
        Predicate<String> longish  = s -> s.length() > 3;

        Predicate<String> notEmptyNotA_and_long = notEmpty.and(startsA.negate()).and(longish);

        for (String s : new String[] { "Alice", "Bob", "", "Charlie", "An", "Anna" }) {
            System.out.println("'" + s + "' -> " + notEmptyNotA_and_long.test(s));
        }
    }
}
