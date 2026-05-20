class User {

    int userId;
    String name;

    User(int userId, String name) {

        this.userId = userId;
        this.name = name;
    }
}

class Passport {

    int passportId;
    int userId;

    Passport(int passportId, int userId) {

        this.passportId = passportId;
        this.userId = userId;
    }
}

public class OneToOneRelationshipExample {

    public static void main(String[] args) {

        User user =
                new User(1, "Tejas");

        Passport passport =
                new Passport(101, 1);

        System.out.println(
                user.name
                        + " owns passport "
                        + passport.passportId
        );
    }
}