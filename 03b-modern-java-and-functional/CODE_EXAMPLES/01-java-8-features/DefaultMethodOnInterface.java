import java.util.ArrayList;
import java.util.List;

public class DefaultMethodOnInterface {

    // An interface that ships a default implementation. Existing implementers
    // get the method "for free" without code changes.
    interface Greeter {
        String name();
        default String greet() { return "hello, " + name(); }
    }

    // Existing implementer -- never touched, never knew greet() was added.
    static class Plain implements Greeter {
        public String name() { return "Plain"; }
    }

    // Newer implementer -- chooses to override.
    static class Loud implements Greeter {
        public String name() { return "Loud"; }
        public String greet() { return "HEY, " + name().toUpperCase() + "!"; }
    }

    public static void main(String[] args) {
        List<Greeter> people = new ArrayList<>();
        people.add(new Plain());
        people.add(new Loud());

        for (Greeter g : people) System.out.println(g.greet());

        System.out.println();
        System.out.println("Default methods let JDK teams (and you) extend interfaces");
        System.out.println("without breaking every existing implementer.");
    }
}
