public class ThrowVsThrowsExample {

    static void validateAge(int age)
            throws Exception {

        if (age < 18) {

            throw new Exception(
                    "Age Must Be 18 or Above"
            );
        }

        System.out.println(
                "Validation Successful"
        );
    }

    public static void main(String[] args) {

        try {

            validateAge(16);

        } catch (Exception exception) {

            System.out.println(
                    exception.getMessage()
            );
        }
    }
}