import java.util.Map;
import java.util.TreeMap;

public class TreeMapExample {

    public static void main(String[] args) {

        Map<Integer, String> users =
                new TreeMap<>();

        users.put(103, "Amit");

        users.put(101, "Tejas");

        users.put(102, "Rahul");

        System.out.println(users);
    }
}