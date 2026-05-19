public class StatementExecutionExample {

    public static void main(String[] args) {

        String query =
                "SELECT * FROM users";

        System.out.println(
                "Executing Query:"
        );

        System.out.println(query);

        System.out.println(
                "Processing ResultSet"
        );
    }
}