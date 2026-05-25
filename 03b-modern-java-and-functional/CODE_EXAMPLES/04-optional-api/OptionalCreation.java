import java.util.Optional;

public class OptionalCreation {

    public static void main(String[] args) {
        // 1) of -- value must not be null. Use when you KNOW you have a value.
        Optional<String> defined = Optional.of("hello");
        System.out.println("of:         " + defined);

        // 2) ofNullable -- value may be null. Use when wrapping an unknown source.
        String maybe = readFromUnknownSource();
        Optional<String> wrapped = Optional.ofNullable(maybe);
        System.out.println("ofNullable: " + wrapped);

        // 3) empty -- explicit "not found".
        Optional<String> none = Optional.empty();
        System.out.println("empty:      " + none);

        // Trap: of(null) throws NPE on construction.
        try {
            Optional.of(null);
        } catch (NullPointerException e) {
            System.out.println("Optional.of(null) -> NullPointerException (use ofNullable instead).");
        }
    }

    static String readFromUnknownSource() {
        return Math.random() < 0.5 ? "found" : null;
    }
}
