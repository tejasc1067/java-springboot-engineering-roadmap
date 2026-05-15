import java.util.Optional;

public class OptionalCreationExample {

    public static void main(String[] args) {

        Optional<String> user =
                Optional.of("Tejas");

        Optional<String> nullableUser =
                Optional.ofNullable(null);

        System.out.println(user);

        System.out.println(nullableUser);
    }
}