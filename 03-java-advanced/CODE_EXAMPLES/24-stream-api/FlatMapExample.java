import java.util.List;
import java.util.stream.Collectors;

public class FlatMapExample {

    public static void main(String[] args) {

        List<List<String>> departments =
                List.of(
                        List.of("Tejas", "Rahul"),
                        List.of("Amit", "Priya")
                );

        List<String> employees =
                departments.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList());

        System.out.println(
                employees
        );
    }
}