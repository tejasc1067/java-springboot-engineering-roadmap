import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MethodReferenceFlavors {

    public static void main(String[] args) {
        // 1) Reference to a STATIC method.
        //    Integer::parseInt  is shorthand for  s -> Integer.parseInt(s)
        Function<String, Integer> parse = Integer::parseInt;
        System.out.println("static method:    parse(\"42\") = " + parse.apply("42"));

        // 2) Reference to an instance method of a SPECIFIC object.
        //    System.out::println  is shorthand for  x -> System.out.println(x)
        Consumer<String> printer = System.out::println;
        printer.accept("specific-object instance method");

        // 3) Reference to an instance method of an ARBITRARY object of a type.
        //    String::length  is shorthand for  s -> s.length()
        //    The lambda parameter BECOMES the receiver.
        Function<String, Integer> length = String::length;
        System.out.println("arbitrary-instance method: length(\"hello\") = " + length.apply("hello"));

        // 4) Constructor reference.
        //    ArrayList::new  is shorthand for  () -> new ArrayList<>()
        Supplier<List<String>> factory = ArrayList::new;
        List<String> fresh = factory.get();
        fresh.add("from factory");
        System.out.println("constructor reference: " + fresh);
    }
}
