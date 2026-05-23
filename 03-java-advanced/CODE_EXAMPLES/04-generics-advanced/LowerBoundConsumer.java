import java.util.ArrayList;
import java.util.List;

public class LowerBoundConsumer {

    static void addIntegers(List<? super Integer> dest) {
        dest.add(1);
        dest.add(2);
        dest.add(3);
        // Integer x = dest.get(0);   ← compile error: only safe as Object
        //                              consumer = write-only (for T or subtype)
    }

    public static void main(String[] args) {
        List<Integer> ints = new ArrayList<>();
        List<Number>  nums = new ArrayList<>();
        List<Object>  objs = new ArrayList<>();

        addIntegers(ints);
        addIntegers(nums);
        addIntegers(objs);

        System.out.println("List<Integer> after add: " + ints);
        System.out.println("List<Number>  after add: " + nums);
        System.out.println("List<Object>  after add: " + objs);
    }
}
