import java.util.ArrayList;
import java.util.List;

public class IntSubtractionOverflow {

    record Score(String label, int value) {}

    public static void main(String[] args) {
        List<Score> scores = new ArrayList<>(List.of(
            new Score("near MAX", Integer.MAX_VALUE),
            new Score("near MIN", Integer.MIN_VALUE),
            new Score("zero",     0)
        ));

        scores.sort((a, b) -> a.value() - b.value());

        System.out.println("buggy sort using a - b:");
        scores.forEach(s -> System.out.println("  " + s));

        System.out.println();
        System.out.println("MAX - MIN overflows and wraps. The 'difference' goes negative.");
        System.out.println("So MAX sorts BEFORE MIN -- wildly wrong order.");
        System.out.println();
        System.out.println("See IntegerCompareSafe.java for the fix.");
    }
}
