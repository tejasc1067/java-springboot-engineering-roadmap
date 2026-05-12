final class User {

    private final int id;

    private final String name;

    User(int id,
         String name) {

        this.id = id;

        this.name = name;
    }

    public int getId() {

        return id;
    }

    public String getName() {

        return name;
    }
}

public class ImmutableObjectExample {

    public static void main(String[] args) {

        User user =
                new User(101, "Tejas");

        System.out.println(
                user.getName()
        );
    }
}