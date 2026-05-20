public class PreparedStatementExample {

    public static void main(String[] args) {

        String query =
                "SELECT * FROM users " +
                        "WHERE email = ?";

        System.out.println(
                "PreparedStatement Query:"
        );

        System.out.println(query);

        System.out.println(
                "Parameter Bound Securely"
        );
    }
}