import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Statement has three execute methods. Picking the right one makes intent clear.
//
//   executeQuery(sql)   → for SELECT. Returns a ResultSet.
//   executeUpdate(sql)  → for INSERT/UPDATE/DELETE and DDL. Returns int (rows affected).
//   execute(sql)        → generic. Returns boolean (true if there's a ResultSet).
//                         Use only when you don't know the kind of statement at compile time.
public class ExecuteVariations {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            // executeUpdate for DDL (returns 0 — no rows affected for DDL)
            int ddlResult = stmt.executeUpdate("CREATE TABLE items (id INT, name VARCHAR(50))");
            System.out.println("DDL result (always 0): " + ddlResult);

            // executeUpdate for INSERT
            int inserted = stmt.executeUpdate("INSERT INTO items VALUES (1, 'A'), (2, 'B'), (3, 'C')");
            System.out.println("INSERT inserted: " + inserted + " rows");

            // executeQuery for SELECT
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM items")) {
                rs.next();
                System.out.println("SELECT count: " + rs.getInt(1));
            }

            // execute() — generic. Useful when the statement type isn't fixed
            // (e.g. an admin tool running arbitrary user-typed SQL).
            String anySql = "SELECT name FROM items WHERE id = 2";
            boolean hasResultSet = stmt.execute(anySql);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    while (rs.next()) {
                        System.out.println("execute() returned rows: " + rs.getString(1));
                    }
                }
            } else {
                System.out.println("execute() updated rows: " + stmt.getUpdateCount());
            }

            // Wrong method = exception. executeUpdate on a SELECT throws:
            try {
                stmt.executeUpdate("SELECT * FROM items");
            } catch (Exception e) {
                System.out.println("\nexecuteUpdate on SELECT failed (as expected): " + e.getClass().getSimpleName());
            }
        }
    }
}
