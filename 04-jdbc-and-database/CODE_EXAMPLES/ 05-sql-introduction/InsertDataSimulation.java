import java.util.ArrayList;
import java.util.List;

public class InsertDataSimulation {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        users.add("Tejas");
        users.add("Rahul");

        System.out.println(
                "Inserted Users: " + users
        );
    }
}