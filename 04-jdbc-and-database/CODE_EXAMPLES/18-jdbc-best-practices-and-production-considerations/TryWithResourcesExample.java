import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TryWithResourcesExample {

    private static final String URL =
            "jdbc:mysql://localhost:3306/appdb";

    private static final String USERNAME =
            "root";

    private static final String PASSWORD =
            "password";

    public static void main(String[] args) {

        String query =
                "SELECT id, name FROM users";

        try (
                Connection connection =
                        DriverManager.getConnection(
                                URL,
                                USERNAME,
                                PASSWORD
                        );

                PreparedStatement preparedStatement =
                        connection.prepareStatement(query);

                ResultSet resultSet =
                        preparedStatement.executeQuery()
        ) {

            while (resultSet.next()) {

                System.out.println(
                        resultSet.getInt("id")
                                + " - "
                                + resultSet.getString("name")
                );
            }

        } catch (SQLException exception) {

            exception.printStackTrace();
        }
    }
}