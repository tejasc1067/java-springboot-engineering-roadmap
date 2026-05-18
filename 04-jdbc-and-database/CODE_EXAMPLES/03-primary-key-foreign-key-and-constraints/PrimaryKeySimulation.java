class User {

    int userId;
    String name;

    User(int userId, String name) {

        this.userId = userId;
        this.name = name;
    }
}

public class PrimaryKeySimulation {

    public static void main(String[] args) {

        User user1 =
                new User(1, "Tejas");

        User user2 =
                new User(2, "Rahul");

        System.out.println(
                user1.userId
        );

        System.out.println(
                user2.userId
        );
    }
}