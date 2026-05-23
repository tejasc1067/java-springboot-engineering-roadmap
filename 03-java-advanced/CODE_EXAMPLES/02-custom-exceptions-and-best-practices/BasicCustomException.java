public class BasicCustomException {

    static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
        public UserNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String findUser(long id) {
        if (id == 42) {
            return "Alice";
        }
        throw new UserNotFoundException("user " + id + " does not exist");
    }

    public static void main(String[] args) {
        System.out.println("user 42 = " + findUser(42));

        try {
            findUser(99);
        } catch (UserNotFoundException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }
}
