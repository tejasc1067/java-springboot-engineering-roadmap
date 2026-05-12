import java.util.ArrayList;
import java.util.List;

public class BasicArrayListExample {

    public static void main(String[] args) {

        List<String> technologies =
                new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("Java");

        System.out.println(technologies);
    }
}