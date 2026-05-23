// When a lambda body is "just call this existing method," you can write a
// method reference instead: ClassName::method or instance::method.

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class MethodReferences {
    public static void main(String[] args) {

        // 1. Static method:  ClassName::staticMethod
        //    Lambda:         s -> Integer.parseInt(s)
        Function<String, Integer> parse1 = s -> Integer.parseInt(s);
        Function<String, Integer> parse2 = Integer::parseInt;
        System.out.println("parse(\"42\") = " + parse2.apply("42"));

        // 2. Bound instance method:  instance::method
        //    Lambda:                 s -> System.out.println(s)
        List<String> names = List.of("alice", "bob");
        names.forEach(System.out::println);

        // 3. Unbound instance method:  ClassName::instanceMethod
        //    Lambda:                   s -> s.length()
        Function<String, Integer> len1 = s -> s.length();
        Function<String, Integer> len2 = String::length;
        System.out.println("\nlen(\"hello\") = " + len2.apply("hello"));

        // 4. Constructor reference:  ClassName::new
        //    Lambda:                  () -> new ArrayList<>()
        Supplier<List<String>> makeList = ArrayList::new;
        List<String> created = makeList.get();
        created.add("hi");
        System.out.println("constructor reference made: " + created);
    }
}
