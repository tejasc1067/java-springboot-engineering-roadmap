import java.util.HashSet;
import java.util.Set;

public class RecordAutoEquals {

    record User(long id, String email) {}

    public static void main(String[] args) {
        Set<User> users = new HashSet<>();
        users.add(new User(1, "alice@example.com"));
        users.add(new User(1, "alice@example.com"));
        users.add(new User(2, "bob@example.com"));

        System.out.println("set size: " + users.size() + "   (1 and 2 — duplicate of id=1 collapsed)");
        for (User u : users) {
            System.out.println("  - " + u);
        }

        System.out.println();
        System.out.println("Records generate equals/hashCode from the components automatically.");
        System.out.println("Fields are final, so the 'mutation breaks the set' bug is impossible by construction.");
    }
}
