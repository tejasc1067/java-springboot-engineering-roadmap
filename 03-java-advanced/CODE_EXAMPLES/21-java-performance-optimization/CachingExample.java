import java.util.HashMap;
import java.util.Map;

public class CachingExample {

    private static final Map<Integer, String>
            cache =
            new HashMap<>();

    public static String fetchUser(int id) {

        if (cache.containsKey(id)) {

            return cache.get(id);
        }

        String user =
                "User-" + id;

        cache.put(id, user);

        return user;
    }

    public static void main(String[] args) {

        System.out.println(
                fetchUser(101)
        );

        System.out.println(
                fetchUser(101)
        );
    }
}