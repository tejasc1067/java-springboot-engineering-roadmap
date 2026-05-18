import java.util.ArrayList;
import java.util.List;

public class SimplePersistenceSimulation {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        users.add("Tejas");
        users.add("Rahul");

        System.out.println(
                "Stored Users: " + users
        );
    }
}