import java.util.Optional;

public class BackendOptionalWorkflowExample {

    public static Optional<String> findUserById(
            int userId
    ) {

        if (userId == 101) {

            return Optional.of("Tejas");
        }

        return Optional.empty();
    }

    public static void main(String[] args) {

        Optional<String> user =
                findUserById(101);

        user.ifPresent(
                value -> System.out.println(
                        "User Found: " + value
                )
        );

        String fallbackUser =
                findUserById(500)
                        .orElse("Guest User");

        System.out.println(fallbackUser);
    }
}