import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapExample {

    public static void main(String[] args) {

        Map<Integer, String> users =
                new LinkedHashMap<>();

        users.put(101, "Tejas");

        users.put(102, "Rahul");

        users.put(103, "Amit");

        System.out.println(users);
    }
}