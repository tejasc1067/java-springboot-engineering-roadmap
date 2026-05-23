import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComparableNaturalOrder {

    static class User implements Comparable<User> {
        final long id;
        final String name;
        User(long id, String name) { this.id = id; this.name = name; }

        @Override
        public int compareTo(User other) {
            return Long.compare(this.id, other.id);
        }

        @Override
        public String toString() { return id + ":" + name; }
    }

    public static void main(String[] args) {
        List<User> users = new ArrayList<>();
        users.add(new User(3, "Charlie"));
        users.add(new User(1, "Alice"));
        users.add(new User(2, "Bob"));

        Collections.sort(users);

        System.out.println("sorted by natural order (id):");
        users.forEach(u -> System.out.println("  " + u));
    }
}
