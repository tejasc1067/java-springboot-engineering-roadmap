class BaseService {

    void connectDatabase() {

        System.out.println(
                "Database Connection Established"
        );
    }
}

class UserService extends BaseService {

    void registerUser() {

        super.connectDatabase();

        System.out.println(
                "User Registered Successfully"
        );
    }
}

public class BackendInheritanceExample {

    public static void main(String[] args) {

        UserService userService =
                new UserService();

        userService.registerUser();
    }
}