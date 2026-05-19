public class JdbcDeleteExample {

    public static void main(String[] args) {

        String deleteQuery =
                "DELETE FROM users " +
                        "WHERE id=1";

        System.out.println(
                "Executing Delete Query"
        );

        System.out.println(deleteQuery);

        System.out.println(
                "User Deleted Successfully"
        );
    }
}