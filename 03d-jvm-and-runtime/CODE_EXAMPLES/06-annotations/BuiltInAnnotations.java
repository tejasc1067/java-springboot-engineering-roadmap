import java.util.ArrayList;
import java.util.List;

public class BuiltInAnnotations {

    static class Base { public String name() { return "Base"; } }

    static class Sub extends Base {
        @Override                                       // compile-time check: must override
        public String name() { return "Sub"; }
    }

    @Deprecated(since = "2.0", forRemoval = true)
    static String oldApi() { return "old"; }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        List<T> out = new ArrayList<>();
        for (T t : items) out.add(t);
        return out;
    }

    @FunctionalInterface
    interface Greeter {
        String greet(String name);
        default String greetLoud(String n) { return greet(n).toUpperCase(); }
    }

    @SuppressWarnings("rawtypes")
    static List untypedExample() { return new ArrayList(); }

    public static void main(String[] args) {
        System.out.println("override:           " + new Sub().name());
        System.out.println("deprecated returns: " + oldApi());
        System.out.println("safevarargs:        " + listOf("a", "b", "c"));
        Greeter g = name -> "hello, " + name;
        System.out.println("functional iface:   " + g.greet("Alice"));
        System.out.println("suppress raw:       " + untypedExample());
        System.out.println();
        System.out.println("None of these annotations change runtime behavior. They're compiler hints.");
    }
}
