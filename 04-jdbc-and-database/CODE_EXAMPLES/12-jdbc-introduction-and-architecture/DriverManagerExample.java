public class DriverManagerExample {

    public static void main(String[] args) {

        String url =
                "jdbc:mysql://localhost:3306/appdb";

        String username =
                "root";

        System.out.println(
                "Connecting To Database Using:"
        );

        System.out.println(url);

        System.out.println(username);
    }
}