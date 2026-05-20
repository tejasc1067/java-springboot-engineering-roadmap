import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariCpConfigurationExample {

    private static final String URL =
            "jdbc:mysql://localhost:3306/appdb";

    private static final String USERNAME =
            "root";

    private static final String PASSWORD =
            "password";

    public static void main(String[] args) {

        HikariConfig hikariConfig =
                new HikariConfig();

        hikariConfig.setJdbcUrl(URL);
        hikariConfig.setUsername(USERNAME);
        hikariConfig.setPassword(PASSWORD);

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);

        HikariDataSource dataSource =
                new HikariDataSource(hikariConfig);

        try (
                Connection connection =
                        dataSource.getConnection()
        ) {

            System.out.println(
                    "Connection Retrieved From HikariCP Pool"
            );

        } catch (SQLException exception) {

            exception.printStackTrace();

        } finally {

            dataSource.close();
        }
    }
}