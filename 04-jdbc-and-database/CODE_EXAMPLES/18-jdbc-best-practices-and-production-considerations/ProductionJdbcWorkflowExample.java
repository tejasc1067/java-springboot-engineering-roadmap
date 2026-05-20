import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductionJdbcWorkflowExample {

    public static void main(String[] args) {

        HikariConfig config =
                new HikariConfig();

        config.setJdbcUrl(
                System.getenv("DB_URL")
        );

        config.setUsername(
                System.getenv("DB_USERNAME")
        );

        config.setPassword(
                System.getenv("DB_PASSWORD")
        );

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