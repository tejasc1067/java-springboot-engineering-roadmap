import java.util.List;

public class BoundedTypeParameter {

    static <T extends Number> T firstNumber(List<T> nums) {
        if (nums.isEmpty()) return null;
        return nums.get(0);
    }

    static <T extends Number & Comparable<T>> T maxOf(List<T> nums) {
        T best = nums.get(0);
        for (T n : nums) {
            if (n.compareTo(best) > 0) best = n;
        }
        return best;
    }

    public static void main(String[] args) {
        Integer firstInt = firstNumber(List.of(10, 20, 30));
        Double  firstDouble = firstNumber(List.of(1.1, 2.2, 3.3));
        System.out.println("first int returned as Integer: " + firstInt);
        System.out.println("first double returned as Double: " + firstDouble);

        System.out.println();
        System.out.println("max of ints:    " + maxOf(List.of(3, 1, 4, 1, 5, 9, 2, 6)));
        System.out.println("max of doubles: " + maxOf(List.of(1.5, 7.7, 3.3)));

        System.out.println();
        System.out.println("named T lets the return type stay specific (Integer in, Integer out).");
        System.out.println("with `List<? extends Number>` the return would only be Number.");
    }
}
