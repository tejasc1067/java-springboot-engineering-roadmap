import java.io.IOException;

public class CheckedDomainException {

    static class ConfigUnavailableException extends Exception {
        public ConfigUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    static String loadFromConfigServer(String key) throws ConfigUnavailableException {
        try {
            throw new IOException("config server unreachable");
        } catch (IOException e) {
            throw new ConfigUnavailableException("could not fetch " + key, e);
        }
    }

    static String fetchSettingWithFallback(String key) {
        try {
            return loadFromConfigServer(key);
        } catch (ConfigUnavailableException e) {
            System.out.println("  fallback engaged because: " + e.getMessage());
            return "default-" + key;
        }
    }

    public static void main(String[] args) {
        String value = fetchSettingWithFallback("feature.flag.x");
        System.out.println("resolved value: " + value);
        System.out.println();
        System.out.println("checked exception forced the caller to decide: retry, fall back, or fail.");
    }
}
