import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPoolLifecycleExample {

    public static void main(String[] args) {

        HikariConfig config =
                new HikariConfig();

        config.setJdbcUrl(
                "jdbc:mysql://localhost:3306/appdb"
        );

        config.setUsername("root");
        config.setPassword("password");

        HikariDataSource dataSource =
                new HikariDataSource(config);

        try (
                Connection connection =
                        dataSource.getConnection()
        ) {

            System.out.println(
                    "Connection Checked Out From Pool"
            );

        } catch (SQLException exception) {

            exception.printStackTrace();

        } finally {

            dataSource.close();

            System.out.println(
                    "Pool Shutdown Successfully"
            );
        }
    }
}