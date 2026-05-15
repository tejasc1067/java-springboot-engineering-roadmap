import java.util.Optional;

public class OptionalFallbackExample {

    public static void main(String[] args) {

        Optional<String> user =
                Optional.empty();

        String result =
                user.orElse("Default User");

        System.out.println(result);

        String lazyResult =
                user.orElseGet(
                        () -> "Generated User"
                );

        System.out.println(lazyResult);
    }
}