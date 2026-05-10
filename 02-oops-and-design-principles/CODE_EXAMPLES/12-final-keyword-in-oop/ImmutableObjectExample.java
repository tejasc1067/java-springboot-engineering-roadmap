final class User {

    private final int userId;

    private final String username;

    User(int userId,
         String username) {

        this.userId = userId;

        this.username = username;
    }

    public int getUserId() {

        return userId;
    }

    public String getUsername() {

        return username;
    }
}

public class ImmutableObjectExample {

    public static void main(String[] args) {

        User user =
                new User(
                        101,
                        "Tejas"
                );

        System.out.println(
                user.getUserId()
        );

        System.out.println(
                user.getUsername()
        );
    }
}