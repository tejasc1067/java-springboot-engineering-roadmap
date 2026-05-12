import java.util.HashSet;
import java.util.Set;

public class HashSetBasicExample {

    public static void main(String[] args) {

        Set<String> technologies =
                new HashSet<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("Java");

        System.out.println(technologies);
    }
}