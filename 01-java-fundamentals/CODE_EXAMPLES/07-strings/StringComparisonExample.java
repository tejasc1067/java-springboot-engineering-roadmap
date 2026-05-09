public class StringComparisonExample {

    public static void main(String[] args) {

        String first = new String("Java");

        String second = new String("Java");

        System.out.println("Using == : " + (first == second));

        System.out.println("Using equals(): " + first.equals(second));
    }
}