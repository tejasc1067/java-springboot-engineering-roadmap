public class MethodOverloadingExample {

    // Method for integer addition
    public static int add(int a, int b) {

        return a + b;
    }

    // Overloaded method for double addition
    public static double add(double a, double b) {

        return a + b;
    }

    public static void main(String[] args) {

        int intResult = add(10, 20);

        double doubleResult = add(10.5, 20.5);

        System.out.println("Integer Addition: " + intResult);

        System.out.println("Double Addition: " + doubleResult);
    }
}