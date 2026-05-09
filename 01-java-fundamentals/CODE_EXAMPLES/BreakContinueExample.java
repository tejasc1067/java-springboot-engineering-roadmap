public class BreakContinueExample {

    public static void main(String[] args) {

        // Processing user IDs from 1 to 10
        for (int userId = 1; userId <= 10; userId++) {

            // Skip blocked user
            if (userId == 5) {

                System.out.println("Skipping blocked User ID: " + userId);

                continue;
            }

            // Stop processing at critical condition
            if (userId == 8) {

                System.out.println("Stopping processing at User ID: " + userId);

                break;
            }

            System.out.println("Processing User ID: " + userId);
        }
    }
}