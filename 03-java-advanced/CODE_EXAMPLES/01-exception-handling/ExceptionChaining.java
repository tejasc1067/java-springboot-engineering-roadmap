import java.sql.SQLException;

public class ExceptionChaining {

    static class RepositoryException extends RuntimeException {
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
        public RepositoryException(String message) {
            super(message);
        }
    }

    static void fetchFromDatabase() throws SQLException {
        throw new SQLException("connection refused: localhost:5432");
    }

    static void withChain() {
        try {
            fetchFromDatabase();
        } catch (SQLException e) {
            throw new RepositoryException("could not load user 42", e);
        }
    }

    static void withoutChain() {
        try {
            fetchFromDatabase();
        } catch (SQLException e) {
            throw new RepositoryException("could not load user 42");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== WITH chain (preserves cause) ===");
        try {
            withChain();
        } catch (RepositoryException e) {
            e.printStackTrace(System.out);
        }

        System.out.println();
        System.out.println("=== WITHOUT chain (loses cause -- SQLException invisible) ===");
        try {
            withoutChain();
        } catch (RepositoryException e) {
            e.printStackTrace(System.out);
        }
    }
}
