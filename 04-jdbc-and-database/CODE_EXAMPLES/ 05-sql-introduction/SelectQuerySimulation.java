import java.util.Arrays;
import java.util.List;

public class SelectQuerySimulation {

    public static void main(String[] args) {

        List<String> users =
                Arrays.asList(
                        "Tejas",
                        "Rahul",
                        "Amit"
                );

        for (String user : users) {

            System.out.println(user);
        }
    }
}