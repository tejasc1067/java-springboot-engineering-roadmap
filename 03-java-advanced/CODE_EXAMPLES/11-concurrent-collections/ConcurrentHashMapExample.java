import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapExample {

    public static void main(String[] args) {

        Map<Integer, String> sessions =
                new ConcurrentHashMap<>();

        sessions.put(101, "ACTIVE");

        sessions.put(102, "EXPIRED");

        System.out.println(sessions);

        System.out.println(
                sessions.get(101)
        );
    }
}