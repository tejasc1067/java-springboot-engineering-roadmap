import java.util.ArrayList;

public class ForEachExample {

    public static void main(String[] args) {

        ArrayList<String> technologies = new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("AWS");

        technologies.forEach(System.out::println);
    }
}