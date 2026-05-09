import java.util.HashMap;

public class HashMapNullHandlingExample {

    public static void main(String[] args) {

        HashMap<Integer, String> users = new HashMap<>();

        users.put(101, "Tejas");

        users.put(102, "Rahul");

        // Retrieving missing key
        String user = users.get(999);

        // Null check
        if (user != null) {

            System.out.println(user);

        } else {

            System.out.println("User Not Found");
        }
    }
}