// Switch expression (Java 14+). The modern form. Use this in new code.
//
//   * Arrow `->` instead of `case X:` + `break;`
//   * The whole switch produces a value
//   * No fall-through bugs possible
//   * The compiler enforces that every case is covered (for enums and sealed types)

public class SwitchExpression {
    public static void main(String[] args) {

        for (int day = 1; day <= 7; day++) {
            String name = switch (day) {
                case 1 -> "Monday";
                case 2 -> "Tuesday";
                case 3 -> "Wednesday";
                case 4 -> "Thursday";
                case 5 -> "Friday";
                case 6, 7 -> "weekend";        // multiple labels per arm
                default -> "unknown";
            };
            System.out.println(day + " -> " + name);
        }

        // Switch on strings.
        String status = "ERROR";
        int httpCode = switch (status) {
            case "OK"        -> 200;
            case "NOT_FOUND" -> 404;
            case "ERROR"     -> 500;
            default          -> 0;
        };
        System.out.println("\nstatus=" + status + " -> HTTP " + httpCode);

        // For more complex per-case logic, use a block with `yield`.
        int code = 404;
        String message = switch (code) {
            case 200 -> "OK";
            case 404 -> {
                String reason = lookupReason(code);
                yield "not found: " + reason;       // `yield` returns the block's value
            }
            default -> "unknown";
        };
        System.out.println(message);
    }

    static String lookupReason(int code) {
        return "no such resource";
    }
}
