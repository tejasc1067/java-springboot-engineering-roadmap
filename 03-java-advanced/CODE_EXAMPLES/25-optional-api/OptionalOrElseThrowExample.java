import java.util.Optional;

public class OptionalOrElseThrowExample {

    public static void main(String[] args) {

        Optional<String> user =
                Optional.empty();

        String result =
                user.orElseThrow(
                        () -> new RuntimeException(
                                "User Not Found"
                        )
                );

        System.out.println(result);
    }
}