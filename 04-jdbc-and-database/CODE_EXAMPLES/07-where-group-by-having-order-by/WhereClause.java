import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Demonstrates every common WHERE-clause operator on the same dataset.
public class WhereClause {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    age INT,
                    country VARCHAR(20),
                    email VARCHAR(100)
                )
                """);
            stmt.executeUpdate("""
                INSERT INTO users VALUES
                    (1, 'Alice',   30, 'USA',    'alice@example.com'),
                    (2, 'Bob',     17, 'USA',    'bob@example.com'),
                    (3, 'Carol',   45, 'India',  'carol@example.com'),
                    (4, 'David',   22, 'India',  NULL),
                    (5, 'Eve',     28, 'UK',     'eve@example.com'),
                    (6, 'Frank',   55, 'UK',     'frank@example.com')
                """);

            run(stmt, "Adults only",
                    "SELECT name, age FROM users WHERE age >= 18");

            run(stmt, "USA adults",
                    "SELECT name FROM users WHERE country = 'USA' AND age >= 18");

            run(stmt, "USA or UK",
                    "SELECT name, country FROM users WHERE country IN ('USA', 'UK')");

            run(stmt, "Names starting with A or E",
                    "SELECT name FROM users WHERE name LIKE 'A%' OR name LIKE 'E%'");

            run(stmt, "Aged 25 to 40",
                    "SELECT name, age FROM users WHERE age BETWEEN 25 AND 40");

            // NULL handling — see topic 05/NullGotcha.java for the trap.
            run(stmt, "No email on file",
                    "SELECT name FROM users WHERE email IS NULL");
        }
    }

    private static void run(Statement stmt, String label, String sql) throws Exception {
        System.out.println("\n--- " + label + " ---");
        try (ResultSet rs = stmt.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) {
                    sb.append(rs.getString(i)).append("  ");
                }
                System.out.println(sb);
            }
        }
    }
}
