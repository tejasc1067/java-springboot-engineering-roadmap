import java.util.ArrayList;
import java.util.List;

// The modern form of template method: instead of subclassing, pass the varying step in as a lambda.
// This is exactly how Spring's JdbcTemplate works: it opens the connection, runs the SQL,
// loops over rows, closes everything, and translates errors. You supply only "turn one row into an object".

record Book(String title, int year) { }

@FunctionalInterface
interface RowMapper<T> {
    T mapRow(String[] row);
}

class FakeConnection {
    static int open = 0;
    FakeConnection() { open++; }
    List<String[]> execute(String sql) {
        return List.of(new String[] {"Dune", "1965"}, new String[] {"Neuromancer", "not-a-year"});
    }
    void close() { open--; }
}

class MiniJdbcTemplate {
    <T> List<T> query(String sql, RowMapper<T> mapper) {
        FakeConnection connection = new FakeConnection();
        try {
            List<T> results = new ArrayList<>();
            for (String[] row : connection.execute(sql)) results.add(mapper.mapRow(row));
            return results;
        } catch (RuntimeException e) {
            // JdbcTemplate does this too: one exception type for callers, whatever went wrong inside.
            throw new IllegalStateException("query failed: " + sql, e);
        } finally {
            connection.close();
        }
    }
}

public class TemplateCallback {
    public static void main(String[] args) {
        MiniJdbcTemplate jdbc = new MiniJdbcTemplate();
        try {
            jdbc.query("select title, year from book",
                    row -> new Book(row[0], Integer.parseInt(row[1])));  // the only code the caller writes
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage() + " <- caused by " + e.getCause());
        }
        System.out.println("Connections left open: " + FakeConnection.open);
        if (FakeConnection.open != 0) throw new AssertionError("leaked connection");
    }
}
