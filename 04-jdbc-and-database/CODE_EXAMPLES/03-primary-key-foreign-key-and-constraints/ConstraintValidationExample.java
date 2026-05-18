public class ConstraintValidationExample {

    public static void main(String[] args) {

        int age = 16;

        if (age >= 18) {

            System.out.println(
                    "Valid Age"
            );

        } else {

            System.out.println(
                    "Constraint Validation Failed"
            );
        }
    }
}