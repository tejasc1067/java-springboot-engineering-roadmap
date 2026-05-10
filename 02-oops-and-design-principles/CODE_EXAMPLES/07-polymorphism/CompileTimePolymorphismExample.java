class Calculator {

    int multiply(int a, int b) {

        return a * b;
    }

    double multiply(double a, double b) {

        return a * b;
    }
}

public class CompileTimePolymorphismExample {

    public static void main(String[] args) {

        Calculator calculator = new Calculator();

        System.out.println(
                calculator.multiply(10, 20)
        );

        System.out.println(
                calculator.multiply(10.5, 2.5)
        );
    }
}