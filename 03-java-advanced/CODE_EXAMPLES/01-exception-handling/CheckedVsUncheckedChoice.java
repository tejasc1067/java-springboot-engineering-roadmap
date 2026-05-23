import java.io.IOException;

public class CheckedVsUncheckedChoice {

    // CHECKED: caller might recover (retry, fall back to default config)
    static class ConfigLoadException extends Exception {
        public ConfigLoadException(String msg, Throwable cause) { super(msg, cause); }
    }

    // UNCHECKED: caller passed invalid input — no recovery, fix the bug
    static class InvalidPriceException extends RuntimeException {
        public InvalidPriceException(String msg) { super(msg); }
    }

    static String loadConfig(String path) throws ConfigLoadException {
        try {
            if (path.equals("missing.conf")) {
                throw new IOException("no such file");
            }
            return "key=value";
        } catch (IOException e) {
            throw new ConfigLoadException("could not load " + path, e);
        }
    }

    static void setPrice(double price) {
        if (price < 0) {
            throw new InvalidPriceException("price cannot be negative: " + price);
        }
    }

    public static void main(String[] args) {
        try {
            loadConfig("missing.conf");
        } catch (ConfigLoadException e) {
            System.out.println("Recovered from checked exception, using defaults.");
            System.out.println("  cause was: " + e.getCause().getMessage());
        }

        try {
            setPrice(-5.0);
        } catch (InvalidPriceException e) {
            System.out.println("Bug detected by unchecked exception: " + e.getMessage());
        }
    }
}
