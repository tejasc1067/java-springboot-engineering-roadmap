import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComparatorAdHoc {

    record User(long id, String name, int score) {}

    public static void main(String[] args) {
        List<User> users = new ArrayList<>(List.of(
            new User(1, "Charlie", 70),
            new User(2, "Alice",   90),
            new User(3, "Bob",     80)
        ));

        users.sort(Comparator.comparing(User::name));
        System.out.println("by name:");
        users.forEach(u -> System.out.println("  " + u));

        users.sort(Comparator.comparingInt(User::score).reversed());
        System.out.println();
        System.out.println("by score descending:");
        users.forEach(u -> System.out.println("  " + u));

        users.sort(Comparator.comparingLong(User::id));
        System.out.println();
        System.out.println("by id:");
        users.forEach(u -> System.out.println("  " + u));
    }
}
