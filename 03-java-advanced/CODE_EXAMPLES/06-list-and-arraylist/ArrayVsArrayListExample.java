import java.util.ArrayList;
import java.util.List;

public class ArrayVsArrayListExample {

    public static void main(String[] args) {

        // Array

        String[] array =
                new String[2];

        array[0] = "Java";

        array[1] = "Spring";

        // ArrayList

        List<String> technologies =
                new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("AWS");

        System.out.println(
                technologies
        );
    }
}