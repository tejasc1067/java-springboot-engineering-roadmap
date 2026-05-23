public class ParameterizedLogging {

    // Stand-in for any real logger that's currently disabled at DEBUG level.
    // The point: with string concatenation, the message is BUILT even if the
    // logger then discards it. With parameterized logging, the args are deferred.
    static boolean DEBUG_ENABLED = false;

    public static void main(String[] args) {
        int n = 2_000_000;
        int userId = 42;
        String action = "view";

        // 1) String concatenation. Builds the message no matter what.
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            logConcat("user " + userId + " did " + action + " " + i);
        }
        long concatMs = (System.nanoTime() - t1) / 1_000_000;

        // 2) Parameterized. The format + arguments are passed; the message is
        //    only assembled inside the logger AFTER checking the level.
        long t2 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            logParam("user {} did {} {}", userId, action, i);
        }
        long paramMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("string-built  log calls: " + concatMs + " ms");
        System.out.println("parameterized log calls: " + paramMs  + " ms");
        System.out.println();
        System.out.println("Even when the level is OFF, string concatenation still allocates the message.");
        System.out.println("Parameterized loggers defer formatting until they know it's needed.");
    }

    static void logConcat(String message) {
        if (DEBUG_ENABLED) System.out.println(message);
        // else: do nothing -- but the message was already built before this call
    }

    static void logParam(String fmt, Object... args) {
        if (!DEBUG_ENABLED) return;          // exits before formatting
        // (real loggers do this format step here only when the level is enabled)
        Object[] a = args;
        StringBuilder sb = new StringBuilder(fmt.length() + 32);
        int argi = 0;
        for (int i = 0; i < fmt.length(); i++) {
            if (i + 1 < fmt.length() && fmt.charAt(i) == '{' && fmt.charAt(i + 1) == '}') {
                sb.append(a[argi++]); i++;
            } else sb.append(fmt.charAt(i));
        }
        System.out.println(sb);
    }
}
