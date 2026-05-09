public class ArraySumExample {

    public static void main(String[] args) {

        int[] numbers = {10, 20, 30, 40};

        int sum = 0;

        // Calculating total sum
        for (int number : numbers) {

            sum = sum + number;
        }

        System.out.println("Total Sum: " + sum);
    }
}