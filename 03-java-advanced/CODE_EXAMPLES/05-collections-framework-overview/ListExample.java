import java.util.ArrayList;
import java.util.List;

public class ListExample {

    public static void main(String[] args) {

        List<String> technologies =
                new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("Java");

        System.out.println(technologies);
    }
}