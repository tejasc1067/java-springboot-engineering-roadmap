class User {

    private final int userId;

    private final String username;

    public User(int userId, String username) {

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

public class ImmutableThinkingExample {

    public static void main(String[] args) {

        User user =
                new User(
                        101,
                        "Tejas"
                );

        System.out.println(
                "User ID: "
                        + user.getUserId()
        );

        System.out.println(
                "Username: "
                        + user.getUsername()
        );
    }
}