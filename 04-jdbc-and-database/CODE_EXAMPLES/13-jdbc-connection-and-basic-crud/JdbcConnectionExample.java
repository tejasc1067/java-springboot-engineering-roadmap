public class JdbcConnectionExample {

    public static void main(String[] args) {

        String url =
                "jdbc:mysql://localhost:3306/appdb";

        String username =
                "root";

        String password =
                "password";

        System.out.println(
                "Connecting To Database..."
        );

        System.out.println(url);

        System.out.println(
                "Connection Established Successfully"
        );
    }
}