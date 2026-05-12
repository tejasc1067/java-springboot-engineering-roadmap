import java.util.ArrayList;
import java.util.List;

public class CollectionsGenericsExample {

    public static void main(String[] args) {

        List<String> technologies =
                new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("AWS");

        for (String technology : technologies) {

            System.out.println(
                    technology
            );
        }
    }
}