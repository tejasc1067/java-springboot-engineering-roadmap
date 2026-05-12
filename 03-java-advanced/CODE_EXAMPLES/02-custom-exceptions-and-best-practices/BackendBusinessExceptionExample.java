class UserNotFoundException
        extends Exception {

    UserNotFoundException(
            String message
    ) {

        super(message);
    }
}

class UserService {

    void findUser(int userId)
            throws UserNotFoundException {

        if (userId != 101) {

            throw new UserNotFoundException(
                    "User Not Found"
            );
        }

        System.out.println(
                "User Found"
        );
    }
}

public class BackendBusinessExceptionExample {

    public static void main(String[] args) {

        UserService userService =
                new UserService();

        try {

            userService.findUser(999);

        } catch (
                UserNotFoundException exception
        ) {

            System.out.println(
                    exception.getMessage()
            );
        }
    }
}