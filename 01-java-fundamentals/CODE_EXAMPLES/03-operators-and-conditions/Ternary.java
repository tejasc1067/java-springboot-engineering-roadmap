// The ternary operator: condition ? valueIfTrue : valueIfFalse
// Use it for a single value-producing decision. Don't nest ternaries —
// readability collapses immediately.

public class Ternary {
    public static void main(String[] args) {

        int age = 20;
        String label = (age >= 18) ? "adult" : "minor";
        System.out.println(age + " -> " + label);

        // The same with if/else, for comparison.
        String label2;
        if (age >= 18) {
            label2 = "adult";
        } else {
            label2 = "minor";
        }
        System.out.println(age + " -> " + label2 + "   (same answer, four extra lines)");

        // A common helper: pick a non-null default.
        String name = null;
        String display = (name != null) ? name : "guest";
        System.out.println("\ndisplay name = " + display);

        // Don't do this. Nested ternaries are write-only code.
        //   String tier = age < 13 ? "child" : age < 18 ? "teen" : age < 65 ? "adult" : "senior";
        // Use an if/else ladder when you have three or more branches.
    }
}
