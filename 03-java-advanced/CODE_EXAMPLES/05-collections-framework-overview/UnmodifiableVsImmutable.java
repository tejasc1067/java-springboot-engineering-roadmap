import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnmodifiableVsImmutable {

    public static void main(String[] args) {
        List<String> backing = new ArrayList<>(List.of("Alice", "Bob"));
        List<String> view = Collections.unmodifiableList(backing);

        try {
            view.add("Charlie");
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add() rejected — view itself is unmodifiable.");
        }

        backing.add("Charlie");
        System.out.println("but the original is still mutable — view now shows: " + view);
        System.out.println("  (unmodifiable wraps; it doesn't isolate)");

        System.out.println();

        List<String> immutable = List.copyOf(backing);
        backing.add("Dana");
        System.out.println("backing has: " + backing);
        System.out.println("immutable copy has: " + immutable + "   (snapshot — won't change)");

        try {
            immutable.add("Eve");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.copyOf result rejected mutation too.");
        }
    }
}
