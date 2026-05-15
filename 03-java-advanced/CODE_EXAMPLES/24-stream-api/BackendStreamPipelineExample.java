import java.util.List;
import java.util.stream.Collectors;

public class BackendStreamPipelineExample {

    public static void main(String[] args) {

        List<String> users =
                List.of(
                        "tejas",
                        "guest",
                        "admin",
                        "rahul"
                );

        List<String> processedUsers =
                users.stream()
                        .filter(user ->
                                !user.equals("guest")
                        )
                        .map(String::toUpperCase)
                        .sorted()
                        .collect(Collectors.toList());

        System.out.println(
                processedUsers
        );
    }
}