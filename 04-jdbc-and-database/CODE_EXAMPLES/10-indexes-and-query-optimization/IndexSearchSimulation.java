import java.util.HashMap;
import java.util.Map;

public class IndexSearchSimulation {

    public static void main(String[] args) {

        Map<Integer, String> indexedUsers =
                new HashMap<>();

        indexedUsers.put(1, "Tejas");
        indexedUsers.put(2, "Rahul");

        System.out.println(
                indexedUsers.get(2)
        );
    }
}