import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class User {

    private final String name;

    User(String name) {

        this.name = name;
    }

    public String getName() {

        return name;
    }

    @Override
    public String toString() {

        return name;
    }
}

public class ComparatorExample {

    public static void main(String[] args) {

        List<User> users =
                new ArrayList<>();

        users.add(new User("Tejas"));

        users.add(new User("Amit"));

        users.add(new User("Rahul"));

        users.sort(
                Comparator.comparing(
                        User::getName
                )
        );

        System.out.println(users);
    }
}