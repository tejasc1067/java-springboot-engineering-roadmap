import java.util.HashMap;
import java.util.Map;

public class MapExample {

    public static void main(String[] args) {

        Map<Integer, String> users =
                new HashMap<>();

        users.put(101, "Tejas");

        users.put(102, "Rahul");

        users.put(103, "Amit");

        System.out.println(users);

        System.out.println(
                users.get(101)
        );
    }
}