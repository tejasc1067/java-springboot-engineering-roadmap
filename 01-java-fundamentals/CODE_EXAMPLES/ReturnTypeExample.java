public class ReturnTypeExample {

    // Method returning value
    public static int multiplyNumbers(int a, int b) {

        return a * b;
    }

    public static void main(String[] args) {

        int result = multiplyNumbers(5, 4);

        System.out.println("Multiplication Result: " + result);
    }
}