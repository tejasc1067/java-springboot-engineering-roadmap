import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualsHashCodeContractFixed {

    static class User {
        final long id;
        final String email;
        User(long id, String email) { this.id = id; this.email = email; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User other)) return false;
            return id == other.id && Objects.equals(email, other.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, email);
        }
    }

    public static void main(String[] args) {
        Set<User> users = new HashSet<>();
        User a = new User(1, "alice@example.com");
        User b = new User(1, "alice@example.com");

        users.add(a);
        users.add(b);

        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println("a.hashCode() == b.hashCode()? " + (a.hashCode() == b.hashCode()));
        System.out.println("Set size:    " + users.size() + "   <-- 1, correct");
    }
}
