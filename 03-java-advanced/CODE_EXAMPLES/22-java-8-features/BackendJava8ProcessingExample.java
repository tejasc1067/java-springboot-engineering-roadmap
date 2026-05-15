import java.util.List;
import java.util.stream.Collectors;

public class BackendJava8ProcessingExample {

    public static void main(String[] args) {

        List<String> users =
                List.of(
                        "Tejas",
                        "Rahul",
                        "Admin",
                        "Guest"
                );

        List<String> processedUsers =
                users.stream()
                        .filter(user ->
                                !user.equals("Guest")
                        )
                        .map(String::toUpperCase)
                        .collect(Collectors.toList());

        System.out.println(
                processedUsers
        );
    }
}