// Classic if / else if / else ladder. Score-to-grade is the usual demo.
// Always use braces, even for one-liners — it prevents the bug where a
// future edit adds a second statement and the indentation lies about scope.

public class IfElse {
    public static void main(String[] args) {

        int[] scores = {95, 82, 71, 58, 33};

        for (int score : scores) {
            String grade;
            if (score >= 90) {
                grade = "A";
            } else if (score >= 75) {
                grade = "B";
            } else if (score >= 60) {
                grade = "C";
            } else if (score >= 40) {
                grade = "D";
            } else {
                grade = "F";
            }
            System.out.println("score " + score + " -> grade " + grade);
        }

        // The braces-matter bug. Looks fine — but if you add a second statement
        // under the `if`, only the first one is conditional. Always use braces.
        //
        //   if (score >= 90)
        //       System.out.println("excellent");
        //       awardPrize();                  // <- runs unconditionally!
        //
        // The compiler does not warn you. Indentation is not syntax in Java.
    }
}
