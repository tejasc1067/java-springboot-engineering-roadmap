import java.util.ArrayList;
import java.util.List;

public class PecsCopy {

    static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (T item : src) {
            dest.add(item);
        }
    }

    public static void main(String[] args) {
        List<Integer> ints = List.of(1, 2, 3);
        List<Number>  numTarget = new ArrayList<>();
        copy(numTarget, ints);
        System.out.println("Number target after copying Integers: " + numTarget);

        List<Double>  doubles = List.of(1.5, 2.5);
        List<Object>  objTarget = new ArrayList<>();
        copy(objTarget, doubles);
        System.out.println("Object target after copying Doubles: " + objTarget);

        System.out.println();
        System.out.println("Producer (src) extends — read T's out.");
        System.out.println("Consumer (dest) super — write T's in.");
    }
}
