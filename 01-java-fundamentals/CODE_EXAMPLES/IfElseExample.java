public class IfElseExample {

    public static void main(String[] args) {

        int marks = 75;

        // Checking pass or fail
        if (marks >= 40) {

            System.out.println("Student Passed");

        } else {

            System.out.println("Student Failed");
        }

        // Nested if example
        boolean hasIdCard = true;

        if (marks >= 40) {

            if (hasIdCard) {

                System.out.println("Eligible for Exam Certificate");
            }
        }
    }
}