import java.util.ArrayList;
import java.util.List;

public class CrudOperationSimulation {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        // CREATE
        users.add("Tejas");

        // READ
        System.out.println(users);

        // UPDATE
        users.set(0, "Tejas Updated");

        // DELETE
        users.remove(0);

        System.out.println(users);
    }
}