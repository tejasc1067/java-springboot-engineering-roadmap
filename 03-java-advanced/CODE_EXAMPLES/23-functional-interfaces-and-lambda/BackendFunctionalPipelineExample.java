import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BackendFunctionalPipelineExample {

    public static void main(String[] args) {

        List<String> users =
                List.of(
                        "tejas",
                        "rahul",
                        "guest"
                );

        Function<String, String> transformUser =
                user -> user.toUpperCase();

        List<String> processedUsers =
                users.stream()
                        .filter(user ->
                                !user.equals("guest")
                        )
                        .map(transformUser)
                        .collect(Collectors.toList());

        System.out.println(
                processedUsers
        );
    }
}