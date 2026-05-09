public class StackMemoryExample {

    public static void displayMessage() {

        int number = 10;

        System.out.println(
                "Local Variable Stored in Stack: "
                        + number
        );
    }

    public static void main(String[] args) {

        displayMessage();
    }
}