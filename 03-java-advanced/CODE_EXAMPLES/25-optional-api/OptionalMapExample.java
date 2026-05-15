import java.util.Optional;

public class OptionalMapExample {

    public static void main(String[] args) {

        Optional<String> user =
                Optional.of("tejas");

        Optional<String> transformedUser =
                user.map(String::toUpperCase);

        System.out.println(
                transformedUser.get()
        );
    }
}