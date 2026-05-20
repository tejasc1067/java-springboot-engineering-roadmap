import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionReuseExample {

    private static final String URL =
            "jdbc:mysql://localhost:3306/appdb";

    private static final String USERNAME =
            "root";

    private static final String PASSWORD =
            "password";

    public static void main(String[] args) {

        try (
                Connection connection =
                        DriverManager.getConnection(
                                URL,
                                USERNAME,
                                PASSWORD
                        )
        ) {

            System.out.println(
                    "Connection Reused Efficiently"
            );

            System.out.println(
                    "Auto Commit: "
                            + connection.getAutoCommit()
            );

        } catch (SQLException exception) {

            exception.printStackTrace();
        }
    }
}