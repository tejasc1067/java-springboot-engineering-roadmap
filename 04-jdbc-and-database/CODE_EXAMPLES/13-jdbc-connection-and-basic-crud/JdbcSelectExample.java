public class JdbcSelectExample {

    public static void main(String[] args) {

        String selectQuery =
                "SELECT * FROM users";

        System.out.println(
                "Executing Select Query"
        );

        System.out.println(selectQuery);

        System.out.println(
                "Processing ResultSet"
        );

        System.out.println(
                "User -> Tejas"
        );
    }
}