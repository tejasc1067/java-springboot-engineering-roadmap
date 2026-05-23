import java.util.List;

public class UpperBoundProducer {

    static double sumAll(List<? extends Number> nums) {
        double total = 0;
        for (Number n : nums) total += n.doubleValue();
        // nums.add(1);      ← compile error: cannot add to ? extends
        //                     producer = read-only
        return total;
    }

    public static void main(String[] args) {
        System.out.println("sum of List<Integer>: " + sumAll(List.of(1, 2, 3)));
        System.out.println("sum of List<Double>:  " + sumAll(List.of(1.5, 2.5)));
        System.out.println("sum of List<Long>:    " + sumAll(List.of(10L, 20L, 30L)));
    }
}
