public class OperatorsExample {

    public static void main(String[] args) {

        int a = 10;
        int b = 5;

        // Arithmetic Operators
        System.out.println("Addition: " + (a + b));
        System.out.println("Subtraction: " + (a - b));
        System.out.println("Multiplication: " + (a * b));
        System.out.println("Division: " + (a / b));
        System.out.println("Modulus: " + (a % b));

        // Comparison Operators
        System.out.println("Is a greater than b? " + (a > b));
        System.out.println("Is a equal to b? " + (a == b));

        // Logical Operators
        boolean isJavaFun = true;
        boolean isBackendEasy = false;

        System.out.println("Logical AND: " + (isJavaFun && isBackendEasy));
        System.out.println("Logical OR: " + (isJavaFun || isBackendEasy));
    }
}