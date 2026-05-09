import java.util.Optional;

public class OptionalExample {

    public static void main(String[] args) {

        String userName = null;

        Optional<String> optionalUser =
                Optional.ofNullable(userName);

        if (optionalUser.isPresent()) {

            System.out.println(optionalUser.get());

        } else {

            System.out.println("User Name Not Available");
        }
    }
}