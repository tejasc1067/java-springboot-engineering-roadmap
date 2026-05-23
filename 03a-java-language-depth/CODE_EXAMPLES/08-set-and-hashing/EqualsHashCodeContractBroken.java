import java.util.HashSet;
import java.util.Set;

public class EqualsHashCodeContractBroken {

    static class User {
        final long id;
        final String email;
        User(long id, String email) { this.id = id; this.email = email; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User other)) return false;
            return id == other.id;
        }

        // hashCode NOT overridden -- inherits Object's identity hash
    }

    public static void main(String[] args) {
        Set<User> users = new HashSet<>();
        User a = new User(1, "alice@example.com");
        User b = new User(1, "alice@example.com");

        users.add(a);
        users.add(b);

        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println("a.hashCode() == b.hashCode()? " + (a.hashCode() == b.hashCode()));
        System.out.println("Set size:    " + users.size() + "   <-- should be 1, is 2");
        System.out.println();
        System.out.println("equals() said they're the same, but hashCode() sent them to different buckets.");
        System.out.println("HashSet never found the second one in the first one's bucket -- it just stored it.");
        System.out.println("See EqualsHashCodeContractFixed.java.");
    }
}
