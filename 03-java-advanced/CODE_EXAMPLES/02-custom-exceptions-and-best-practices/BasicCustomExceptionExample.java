class InvalidUserException
        extends Exception {

    InvalidUserException(String message) {

        super(message);
    }
}

public class BasicCustomExceptionExample {

    static void validateUser(String username)
            throws InvalidUserException {

        if (username == null) {

            throw new InvalidUserException(
                    "Username Cannot Be Null"
            );
        }

        System.out.println(
                "Valid User"
        );
    }

    public static void main(String[] args) {

        try {

            validateUser(null);

        } catch (InvalidUserException exception) {

            System.out.println(
                    exception.getMessage()
            );
        }
    }
}