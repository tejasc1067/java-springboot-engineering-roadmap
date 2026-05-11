public class BasicTryCatchExample {

    public static void main(String[] args) {

        try {

            int result =
                    10 / 0;

            System.out.println(result);

        } catch (ArithmeticException exception) {

            System.out.println(
                    "Cannot Divide By Zero"
            );
        }

        System.out.println(
                "Program Continues Safely"
        );
    }
}