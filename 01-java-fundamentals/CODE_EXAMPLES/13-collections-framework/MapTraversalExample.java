import java.util.HashMap;
import java.util.Map;

public class MapTraversalExample {

    public static void main(String[] args) {

        HashMap<Integer, String> users = new HashMap<>();

        users.put(101, "Tejas");

        users.put(102, "Rahul");

        users.put(103, "Amit");

        // Traversing HashMap using entrySet
        for (Map.Entry<Integer, String> entry : users.entrySet()) {

            System.out.println(
                    "User ID: " + entry.getKey()
                            + ", Name: " + entry.getValue()
            );
        }
    }
}