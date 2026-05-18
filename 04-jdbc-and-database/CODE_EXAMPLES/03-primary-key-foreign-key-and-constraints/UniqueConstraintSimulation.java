import java.util.HashSet;
import java.util.Set;

public class UniqueConstraintSimulation {

    public static void main(String[] args) {

        Set<String> emails =
                new HashSet<>();

        emails.add("tejas@email.com");

        boolean added =
                emails.add("tejas@email.com");

        if (!added) {

            System.out.println(
                    "Duplicate Email Not Allowed"
            );
        }
    }
}