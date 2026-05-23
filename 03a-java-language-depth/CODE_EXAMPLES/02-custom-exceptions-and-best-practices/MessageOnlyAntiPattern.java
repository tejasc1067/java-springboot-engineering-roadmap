public class MessageOnlyAntiPattern {

    static void serviceCall(int scenario) {
        switch (scenario) {
            case 1 -> throw new RuntimeException("user not found");
            case 2 -> throw new RuntimeException("user is locked");
            case 3 -> throw new RuntimeException("forbidden: missing role admin");
            default -> { return; }
        }
    }

    static String fragileController(int scenario) {
        try {
            serviceCall(scenario);
            return "200 OK";
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg.contains("not found")) return "404 -- " + msg;
            if (msg.contains("locked"))    return "423 -- " + msg;
            if (msg.contains("forbidden")) return "403 -- " + msg;
            return "500 -- " + msg;
        }
    }

    public static void main(String[] args) {
        System.out.println(fragileController(1));
        System.out.println(fragileController(2));
        System.out.println(fragileController(3));
        System.out.println();
        System.out.println("Problem: rename 'forbidden' to 'unauthorized' and the controller silently 500s.");
        System.out.println("See TypedExceptionProper.java for the fix.");
    }
}
