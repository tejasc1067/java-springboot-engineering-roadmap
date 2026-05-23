import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntegerCompareSafe {

    record Score(String label, int value) {}

    public static void main(String[] args) {
        List<Score> scores = new ArrayList<>(List.of(
            new Score("near MAX", Integer.MAX_VALUE),
            new Score("near MIN", Integer.MIN_VALUE),
            new Score("zero",     0)
        ));

        scores.sort(Comparator.comparingInt(Score::value));

        System.out.println("safe sort using Integer.compare (via comparingInt):");
        scores.forEach(s -> System.out.println("  " + s));
    }
}
