public class ThrowKeywordExample {

    public static void validateAge(int age) {

        if (age < 18) {

            throw new ArithmeticException("Age Must Be 18 or Above");
        }

        System.out.println("Validation Successful");
    }

    public static void main(String[] args) {

        validateAge(16);
    }
}