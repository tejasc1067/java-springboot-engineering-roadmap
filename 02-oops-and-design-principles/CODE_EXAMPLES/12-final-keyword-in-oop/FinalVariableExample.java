public class FinalVariableExample {

    public static void main(String[] args) {

        final int MAX_USERS = 100;

        System.out.println(
                "Maximum Users: "
                        + MAX_USERS
        );

        // MAX_USERS = 200; // Not Allowed
    }
}