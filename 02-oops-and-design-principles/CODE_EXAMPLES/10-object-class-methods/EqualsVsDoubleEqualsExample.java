class User {

    String username;

    User(String username) {

        this.username = username;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {

            return true;
        }

        if (obj == null
                || getClass() != obj.getClass()) {

            return false;
        }

        User user = (User) obj;

        return username.equals(user.username);
    }
}

public class EqualsVsDoubleEqualsExample {

    public static void main(String[] args) {

        User user1 =
                new User("Tejas");

        User user2 =
                new User("Tejas");

        System.out.println(
                user1 == user2
        );

        System.out.println(
                user1.equals(user2)
        );
    }
}