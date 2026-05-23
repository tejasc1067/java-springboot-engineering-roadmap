import java.util.ArrayList;
import java.util.List;

public class InvarianceProof {

    static double sumNumbersInvariant(List<Number> nums) {
        double total = 0;
        for (Number n : nums) total += n.doubleValue();
        return total;
    }

    public static void main(String[] args) {
        List<Number> nums = new ArrayList<>();
        nums.add(1);
        nums.add(2.5);
        nums.add(3L);
        System.out.println("sum via List<Number>: " + sumNumbersInvariant(nums));

        List<Integer> ints = List.of(1, 2, 3);
        // sumNumbersInvariant(ints);
        // ^ compile error: List<Integer> is NOT a List<Number>
        //   generics are invariant -- see UpperBoundProducer.java for the fix.
        System.out.println("could not pass List<Integer> directly -- would not compile.");
    }
}
