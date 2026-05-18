class User {

    int id;
    String name;

    User(int id, String name) {

        this.id = id;
        this.name = name;
    }
}

public class DatabaseTableSimulation {

    public static void main(String[] args) {

        User user1 =
                new User(1, "Tejas");

        User user2 =
                new User(2, "Rahul");

        System.out.println(
                user1.name
        );

        System.out.println(
                user2.name
        );
    }
}