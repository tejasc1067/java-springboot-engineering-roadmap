import java.util.ArrayList;
import java.util.List;

public class FullTableScanSimulation {

    public static void main(String[] args) {

        List<String> emails =
                new ArrayList<>();

        emails.add("a@email.com");
        emails.add("b@email.com");
        emails.add("c@email.com");

        String target =
                "c@email.com";

        for (String email : emails) {

            if (email.equals(target)) {

                System.out.println(
                        "Found Email: " + email
                );
            }
        }
    }
}