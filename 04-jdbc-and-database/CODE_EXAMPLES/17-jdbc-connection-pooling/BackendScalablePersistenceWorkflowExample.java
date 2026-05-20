import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BackendScalablePersistenceWorkflowExample {

    private static final String URL =
            "jdbc:mysql://localhost:3306/appdb";

    private static final String USERNAME =
            "root";

    private static final String PASSWORD =
            "password";

    public static void main(String[] args) {

        HikariConfig config =
                new HikariConfig();

        config.setJdbcUrl(URL);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);

        config.setMaximumPoolSize(20);

        HikariDataSource dataSource =
                new HikariDataSource(config);

        String query =
                "SELECT id, name FROM users";

        try (
                Connection connection =
                        dataSource.getConnection();

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

        } finally {

            dataSource.close();
        }
    }
}