import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MutationLosesElement {

    static class User {
        long id;
        String email;
        User(long id, String email) { this.id = id; this.email = email; }

        public void setEmail(String email) { this.email = email; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User other)) return false;
            return id == other.id && Objects.equals(email, other.email);
        }

        @Override
        public int hashCode() { return Objects.hash(id, email); }
    }

    public static void main(String[] args) {
        Set<User> users = new HashSet<>();
        User u = new User(1, "old@example.com");
        users.add(u);

        System.out.println("contains before mutation: " + users.contains(u));

        u.setEmail("new@example.com");

        System.out.println("contains after mutation:  " + users.contains(u)
                + "   <-- false! same reference, can't find itself");
        System.out.println("set still claims size:    " + users.size());
        System.out.println();
        System.out.println("The element is in the set, but at the wrong bucket -- lookup misses it.");
        System.out.println("This is why fields used in hashCode/equals should be final (or use records).");
    }
}
