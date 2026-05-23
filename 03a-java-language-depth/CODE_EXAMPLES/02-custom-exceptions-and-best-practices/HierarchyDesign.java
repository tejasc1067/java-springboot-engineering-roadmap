public class HierarchyDesign {

    static abstract class ApplicationException extends RuntimeException {
        protected ApplicationException(String msg) { super(msg); }
    }

    static abstract class NotFoundException extends ApplicationException {
        protected NotFoundException(String msg) { super(msg); }
    }
    static class UserNotFoundException extends NotFoundException {
        public UserNotFoundException(long id) { super("user " + id + " not found"); }
    }
    static class OrderNotFoundException extends NotFoundException {
        public OrderNotFoundException(long id) { super("order " + id + " not found"); }
    }

    static abstract class ValidationException extends ApplicationException {
        protected ValidationException(String msg) { super(msg); }
    }
    static class InvalidEmailException extends ValidationException {
        public InvalidEmailException(String email) { super("invalid email: " + email); }
    }

    static abstract class BusinessRuleException extends ApplicationException {
        protected BusinessRuleException(String msg) { super(msg); }
    }
    static class OrderAlreadyShippedException extends BusinessRuleException {
        public OrderAlreadyShippedException(long id) { super("order " + id + " already shipped"); }
    }

    static String controllerSimulation(Runnable serviceCall) {
        try {
            serviceCall.run();
            return "200 OK";
        } catch (NotFoundException e) {
            return "404 - " + e.getMessage();
        } catch (ValidationException e) {
            return "400 - " + e.getMessage();
        } catch (BusinessRuleException e) {
            return "422 - " + e.getMessage();
        } catch (ApplicationException e) {
            return "500 - " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        System.out.println(controllerSimulation(() -> { throw new UserNotFoundException(42); }));
        System.out.println(controllerSimulation(() -> { throw new OrderNotFoundException(7); }));
        System.out.println(controllerSimulation(() -> { throw new InvalidEmailException("not-an-email"); }));
        System.out.println(controllerSimulation(() -> { throw new OrderAlreadyShippedException(101); }));
    }
}
