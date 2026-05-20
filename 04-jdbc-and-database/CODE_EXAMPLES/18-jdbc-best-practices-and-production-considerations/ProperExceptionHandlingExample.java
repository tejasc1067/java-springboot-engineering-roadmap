import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProperExceptionHandlingExample {

    private static final String URL =
            "jdbc:mysql://localhost:3306/appdb";

    private static final String USERNAME =
            "root";

    private static final String PASSWORD =
            "password";

    public static void main(String[] args) {

        String updateQuery =
                "UPDATE users SET name = ? WHERE id = ?";

        try (
                Connection connection =
                        DriverManager.getConnection(
                                URL,
                                USERNAME,
                                PASSWORD
                        );

                PreparedStatement preparedStatement =
                        connection.prepareStatement(updateQuery)
        ) {

            connection.setAutoCommit(false);

            preparedStatement.setString(1, "Updated User");
            preparedStatement.setInt(2, 1);

            int rowsUpdated =
                    preparedStatement.executeUpdate();

            connection.commit();

            System.out.println(
                    "Rows Updated: " + rowsUpdated
            );

        } catch (SQLException exception) {

            System.err.println(
                    "Database Operation Failed"
            );

            exception.printStackTrace();
        }
    }
}