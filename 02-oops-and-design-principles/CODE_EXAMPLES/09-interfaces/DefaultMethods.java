// `default` methods (Java 8+) let interfaces ship with concrete implementations.
// Lets you evolve interfaces without breaking every class that implements them.

interface Logger {
    // Required to implement.
    void log(String message);

    // Provided defaults — implementations get these free, can override if they want.
    default void warn(String message) {
        log("[WARN] " + message);
    }
    default void error(String message) {
        log("[ERROR] " + message);
    }
}

class ConsoleLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println(message);
    }
    // No need to write warn() or error() — defaults are used.
}

class LoudLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println("=== " + message + " ===");
    }
    // Override one default; keep the other.
    @Override
    public void error(String message) {
        System.out.println("!!! CRITICAL: " + message);
    }
}

public class DefaultMethods {
    public static void main(String[] args) {
        Logger a = new ConsoleLogger();
        a.log("starting up");
        a.warn("low disk space");
        a.error("disk full");

        System.out.println();

        Logger b = new LoudLogger();
        b.log("starting up");
        b.warn("low disk space");     // uses default
        b.error("disk full");         // uses override
    }
}
