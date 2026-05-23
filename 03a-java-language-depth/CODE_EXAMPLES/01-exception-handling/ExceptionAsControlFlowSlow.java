import java.util.ArrayList;
import java.util.List;

public class ExceptionAsControlFlowSlow {

    public static void main(String[] args) {
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            inputs.add(i % 2 == 0 ? String.valueOf(i) : "x" + i);
        }

        long start = System.nanoTime();
        List<Integer> parsed = new ArrayList<>();
        for (String s : inputs) {
            try {
                parsed.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // expected for half the inputs -- exceptions used as a filter
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Parsed " + parsed.size() + " of " + inputs.size() + " inputs");
        System.out.println("Time using exceptions as filter: " + elapsedMs + " ms");
        System.out.println("Now compare with ValidateFirstFast.java");
    }
}
