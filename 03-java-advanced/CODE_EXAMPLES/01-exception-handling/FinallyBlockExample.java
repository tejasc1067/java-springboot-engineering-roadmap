public class FinallyBlockExample {

    public static void main(String[] args) {

        try {

            int result =
                    10 / 0;

            System.out.println(result);

        } catch (ArithmeticException exception) {

            System.out.println(
                    "Exception Occurred"
            );

        } finally {

            System.out.println(
                    "Finally Block Always Executes"
            );
        }
    }
}