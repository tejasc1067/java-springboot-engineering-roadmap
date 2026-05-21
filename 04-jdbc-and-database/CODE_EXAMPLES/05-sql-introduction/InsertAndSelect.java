import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Insert rows with DML, read them back with DQL. The "I'm a database" moment.
public class InsertAndSelect {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    age INT
                )
                """);

            // DML: insert data. Each executeUpdate returns the number of rows affected.
            int rowsInserted = 0;
            rowsInserted += stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 30)");
            rowsInserted += stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', 17)");
            rowsInserted += stmt.executeUpdate("INSERT INTO users VALUES (3, 'Carol', 25)");
            System.out.println("Rows inserted: " + rowsInserted);

            // DQL: read data.
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT id, name, age FROM users WHERE age >= 18 ORDER BY name")) {

                System.out.println("Adults:");
                while (rs.next()) {
                    System.out.printf("  id=%d  name=%s  age=%d%n",
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("age"));
                }
            }
        }
    }
}
