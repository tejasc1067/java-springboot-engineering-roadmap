public class DoWhileLoopExample {

    public static void main(String[] args) {

        int loginAttempt = 1;

        // Login screen should appear at least once
        do {

            System.out.println("Showing login screen...");

            loginAttempt++;

        } while (loginAttempt <= 1);
    }
}