public class FinallyBlockExample {

    public static void main(String[] args) {

        try {

            int[] numbers = {10, 20, 30};

            System.out.println(numbers[5]);

        } catch (ArrayIndexOutOfBoundsException exception) {

            System.out.println("Invalid Array Index");

        } finally {

            System.out.println("Finally Block Always Executes");
        }
    }
}