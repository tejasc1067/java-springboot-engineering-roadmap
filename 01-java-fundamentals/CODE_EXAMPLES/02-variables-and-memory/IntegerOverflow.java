// Java `int` is 32-bit and signed: max value is 2,147,483,647 (~2.1 billion).
// Going past it wraps around silently — no exception, no warning. This is one
// of the most common production bugs in software that handles large numbers
// (millisecond timestamps, large counters, financial cent values).

public class IntegerOverflow {
    public static void main(String[] args) {

        int maxInt = Integer.MAX_VALUE;
        System.out.println("Integer.MAX_VALUE = " + maxInt);

        // Add 1 and watch the wrap.
        int overflowed = maxInt + 1;
        System.out.println("MAX_VALUE + 1     = " + overflowed + "   (negative — wrapped around)");

        // A real-world trigger: multiplying two innocent-looking ints.
        int seconds = 60 * 60 * 24 * 365;        // seconds in a non-leap year — fits in int
        int millis  = seconds * 1000;            // ...but this overflows because seconds * 1000 > 2.1B
        System.out.println("\nMillis in a year (as int, wrong): " + millis);

        // The fix: do the math in long. Promote at least one operand to long
        // BEFORE the multiplication, or every operand will be int and overflow first.
        long correctMillis = (long) seconds * 1000;
        System.out.println("Millis in a year (as long):       " + correctMillis);

        // Common bug: writing `long bad = seconds * 1000;` — the multiplication
        // still happens in int and overflows, THEN the result is widened to long.
        long bad = seconds * 1000;
        System.out.println("\nThe gotcha (multiply in int, then widen): " + bad + "   (still wrong)");
    }
}
