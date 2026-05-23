public class TryWithResourcesAndSuppressed {

    static class FlakyResource implements AutoCloseable {
        private final String name;
        FlakyResource(String name) { this.name = name; }

        void use() {
            throw new RuntimeException("primary failure in " + name);
        }

        @Override
        public void close() {
            throw new RuntimeException("close failure in " + name);
        }
    }

    public static void main(String[] args) {
        try (FlakyResource r = new FlakyResource("db-conn")) {
            r.use();
        } catch (RuntimeException e) {
            System.out.println("Primary exception: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("  Suppressed: " + suppressed.getMessage());
            }
            System.out.println();
            System.out.println("--- full stack trace ---");
            e.printStackTrace(System.out);
        }
    }
}
