public class TypedExceptionProper {

    static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String msg) { super(msg); }
    }
    static class UserLockedException extends RuntimeException {
        public UserLockedException(String msg) { super(msg); }
    }
    static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }

    static void serviceCall(int scenario) {
        switch (scenario) {
            case 1 -> throw new UserNotFoundException("user 42 not found");
            case 2 -> throw new UserLockedException("user 42 locked since 2026-05-20");
            case 3 -> throw new UnauthorizedException("user 42 missing role admin");
            default -> { return; }
        }
    }

    static String robustController(int scenario) {
        try {
            serviceCall(scenario);
            return "200 OK";
        } catch (UserNotFoundException e) {
            return "404 -- " + e.getMessage();
        } catch (UserLockedException e) {
            return "423 -- " + e.getMessage();
        } catch (UnauthorizedException e) {
            return "403 -- " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        System.out.println(robustController(1));
        System.out.println(robustController(2));
        System.out.println(robustController(3));
        System.out.println();
        System.out.println("Message text can change freely -- types are the contract.");
    }
}
