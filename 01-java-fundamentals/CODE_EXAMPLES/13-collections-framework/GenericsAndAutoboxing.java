// Generics let the compiler enforce element types. Collections hold reference
// types only — primitives are auto-converted to their wrapper objects
// (autoboxing) and back (unboxing).

import java.util.ArrayList;
import java.util.List;

public class GenericsAndAutoboxing {
    public static void main(String[] args) {

        List<Integer> numbers = new ArrayList<>();
        numbers.add(5);          // 5 (int) -> Integer.valueOf(5) automatically
        numbers.add(7);
        numbers.add(2);

        int sum = 0;
        for (int n : numbers) {  // Integer -> int automatically
            sum += n;
        }
        System.out.println("sum = " + sum);

        // Type safety: this won't compile.
        // numbers.add("hello");     // error: incompatible types

        // Without generics ("raw types") — DON'T do this in new code.
        List raw = new ArrayList();
        raw.add("alice");
        raw.add(42);
        // Now reading anything requires a cast, and the wrong cast is a runtime error.
        try {
            Integer n = (Integer) raw.get(0);     // ClassCastException
            System.out.println(n);
        } catch (ClassCastException e) {
            System.out.println("raw-type ClassCastException: " + e.getMessage());
        }

        // Autoboxing surprise: Integer comparison with == is NOT reliable.
        Integer a = 200;
        Integer b = 200;
        System.out.println("\nInteger 200 == 200: " + (a == b) + "   (false — different objects)");
        System.out.println("Integer 200.equals(200): " + a.equals(b) + "   (use equals)");

        // The JVM caches Integers from -128 to 127, so SMALL values happen
        // to compare equal with ==. Coincidence, not a rule. Always equals.
        Integer c = 5;
        Integer d = 5;
        System.out.println("Integer 5   == 5  : " + (c == d) + "   (true — but only by luck)");
    }
}
