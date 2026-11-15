import java.time.LocalDate;
import java.util.List;

// You already use factory methods from the JDK every day. Three things they can do
// that a constructor can't: reuse objects, hide the real class, and have a name.

public class StaticFactoryMethods {
    public static void main(String[] args) {
        // 1. Reuse: Integer.valueOf keeps a cache of -128..127.
        Integer a = Integer.valueOf(127), b = Integer.valueOf(127);
        Integer c = Integer.valueOf(128), d = Integer.valueOf(128);
        System.out.println("valueOf(127) == valueOf(127): " + (a == b) + "   (same cached object)");
        System.out.println("valueOf(128) == valueOf(128): " + (c == d) + "  (two objects - always compare with equals)");

        // 2. Hide the real class: you asked for a List; you got some JDK-internal immutable class.
        List<String> list = List.of("a", "b");
        System.out.println("List.of(...) returned a " + list.getClass().getName());
        try {
            list.add("c");
        } catch (UnsupportedOperationException e) {
            System.out.println("...which rejects add(): the factory chose an immutable implementation for you");
        }

        // 3. A name, and validation: LocalDate.of reads better than a constructor and checks its input.
        System.out.println("LocalDate.of(2026, 2, 28) = " + LocalDate.of(2026, 2, 28));
        try {
            LocalDate.of(2026, 2, 30);
        } catch (java.time.DateTimeException e) {
            System.out.println("LocalDate.of(2026, 2, 30) -> " + e.getMessage());
        }
    }
}
