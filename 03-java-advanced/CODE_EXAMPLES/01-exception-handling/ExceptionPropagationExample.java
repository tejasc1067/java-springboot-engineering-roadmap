public class ExceptionPropagationExample {

    static void methodThree() {

        int result =
                10 / 0;

        System.out.println(result);
    }

    static void methodTwo() {

        methodThree();
    }

    static void methodOne() {

        methodTwo();
    }

    public static void main(String[] args) {

        try {

            methodOne();

        } catch (ArithmeticException exception) {

            System.out.println(
                    "Exception Propagated To Main Method"
            );

            exception.printStackTrace();
        }
    }
}