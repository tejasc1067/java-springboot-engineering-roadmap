import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class User {

    private final int id;

    private final String name;

    User(int id,
         String name) {

        this.id = id;

        this.name = name;
    }

    @Override
    public boolean equals(Object object) {

        if (this == object) {

            return true;
        }

        if (object == null
                || getClass()
                != object.getClass()) {

            return false;
        }

        User user =
                (User) object;

        return id == user.id;
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }
}

public class HashCodeEqualsExample {

    public static void main(String[] args) {

        Set<User> users =
                new HashSet<>();

        users.add(
                new User(101, "Tejas")
        );

        users.add(
                new User(101, "Tejas")
        );

        System.out.println(users.size());
    }
}