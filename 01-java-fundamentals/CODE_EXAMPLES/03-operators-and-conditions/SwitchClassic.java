// Classic switch with explicit `break`. You'll meet this form in older code.
//
// The bug demonstration: a missing `break` causes "fall-through" into the next
// case — almost always unintentional. The compiler does not warn you.

public class SwitchClassic {
    public static void main(String[] args) {

        // Correct version: each case has a break.
        for (int day = 1; day <= 5; day++) {
            String name;
            switch (day) {
                case 1: name = "Monday";    break;
                case 2: name = "Tuesday";   break;
                case 3: name = "Wednesday"; break;
                case 4: name = "Thursday";  break;
                case 5: name = "Friday";    break;
                default: name = "unknown";  break;
            }
            System.out.println(day + " -> " + name);
        }

        // The bug: forget a `break` and the case falls through into the next one.
        System.out.println("\nBuggy version (missing break on case 'A'):");
        char letter = 'A';
        switch (letter) {
            case 'A':
                System.out.println("matched A");
                // no break — falls through!
            case 'B':
                System.out.println("matched B");
                break;
            case 'C':
                System.out.println("matched C");
                break;
        }
        // Output is BOTH "matched A" and "matched B". Surprise.
        //
        // The fix: add `break;` after the println in case 'A', or use the
        // switch expression in SwitchExpression.java which makes this bug
        // impossible.
    }
}
