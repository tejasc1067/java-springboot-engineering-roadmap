import java.util.List;
import java.util.stream.Collectors;

public class MapExample {

    public static void main(String[] args) {

        List<String> users =
                List.of(
                        "tejas",
                        "rahul",
                        "admin"
                );

        List<String> upperCaseUsers =
                users.stream()
                        .map(String::toUpperCase)
                        .collect(Collectors.toList());

        System.out.println(
                upperCaseUsers
        );
    }
}