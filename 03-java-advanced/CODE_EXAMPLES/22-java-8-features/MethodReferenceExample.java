import java.util.List;

public class MethodReferenceExample {

    public static void main(String[] args) {

        List<String> names =
                List.of(
                        "Tejas",
                        "Rahul",
                        "Amit"
                );

        names.forEach(
                System.out::println
        );
    }
}