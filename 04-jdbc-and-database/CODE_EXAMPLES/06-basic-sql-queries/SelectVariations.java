import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// SELECT * vs explicit columns, DISTINCT, LIMIT, column aliases.
public class SelectVariations {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    country VARCHAR(50),
                    age INT
                )
                """);

            stmt.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'Alice',   'USA',    30),
                    (2, 'Bob',     'USA',    25),
                    (3, 'Carol',   'India',  35),
                    (4, 'David',   'India',  28),
                    (5, 'Eve',     'UK',     22)
                """);

            // 1. SELECT * — every column. Convenient but fragile.
            System.out.println("--- SELECT * ---");
            print(stmt, "SELECT * FROM users");

            // 2. Explicit columns — preferred in code.
            System.out.println("\n--- SELECT name, age ---");
            print(stmt, "SELECT name, age FROM users");

            // 3. Column alias with AS — renames the output column.
            //    Useful when joining or computing values.
            System.out.println("\n--- SELECT with alias ---");
            print(stmt, "SELECT name AS user_name, age AS years FROM users");

            // 4. DISTINCT — unique values only.
            System.out.println("\n--- SELECT DISTINCT country ---");
            print(stmt, "SELECT DISTINCT country FROM users");

            // 5. LIMIT — return at most N rows. Critical for pagination.
            System.out.println("\n--- SELECT ... LIMIT 2 ---");
            print(stmt, "SELECT name FROM users LIMIT 2");
        }
    }

    private static void print(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            int columnCount = rs.getMetaData().getColumnCount();
            // print column headers
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(rs.getMetaData().getColumnLabel(i) + "  ");
            }
            System.out.println();
            // print rows
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getString(i) + "  ");
                }
                System.out.println();
            }
        }
    }
}
