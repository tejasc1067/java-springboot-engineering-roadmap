import java.util.ArrayList;
import java.util.List;

public class LambdaSortingExample {

    public static void main(String[] args) {

        List<String> technologies =
                new ArrayList<>();

        technologies.add("Spring");

        technologies.add("Java");

        technologies.add("AWS");

        technologies.sort(
                (first, second) ->
                        first.compareTo(second)
        );

        System.out.println(technologies);
    }
}