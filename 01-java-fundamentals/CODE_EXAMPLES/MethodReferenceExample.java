import java.util.Arrays;
import java.util.List;

public class MethodReferenceExample {

    public static void main(String[] args) {

        List<String> users =
                Arrays.asList("Tejas", "Rahul", "Amit");

        // Method Reference
        users.forEach(System.out::println);
    }
}