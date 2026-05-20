import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SecureJdbcConfigurationExample {

    public static void main(String[] args) {

        String url =
                System.getenv("DB_URL");

        String username =
                System.getenv("DB_USERNAME");

        String password =
                System.getenv("DB_PASSWORD");

        try (
                Connection connection =
                        DriverManager.getConnection(
                                url,
                                username,
                                password
                        )
        ) {

            System.out.println(
                    "Secure Database Connection Established"
            );

        } catch (SQLException exception) {

            exception.printStackTrace();
        }
    }
}