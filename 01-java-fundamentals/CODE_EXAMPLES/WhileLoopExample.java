public class WhileLoopExample {

    public static void main(String[] args) {

        int retryCount = 1;

        // Retry system attempts connection 5 times
        while (retryCount <= 5) {

            System.out.println("Retry Attempt: " + retryCount);

            retryCount++;
        }
    }
}