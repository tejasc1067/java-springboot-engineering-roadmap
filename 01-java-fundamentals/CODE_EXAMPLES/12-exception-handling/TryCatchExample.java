public class TryCatchExample {

    public static void main(String[] args) {

        try {

            int result = 10 / 0;

            System.out.println(result);

        } catch (ArithmeticException exception) {

            System.out.println("Cannot divide by zero");
        }

        System.out.println("Application Continues Running");
    }
}