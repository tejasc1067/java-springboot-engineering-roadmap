import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ValidateFirstFast {

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    public static void main(String[] args) {
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            inputs.add(i % 2 == 0 ? String.valueOf(i) : "x" + i);
        }

        long start = System.nanoTime();
        List<Integer> parsed = new ArrayList<>();
        for (String s : inputs) {
            if (INTEGER.matcher(s).matches()) {
                parsed.add(Integer.parseInt(s));
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Parsed " + parsed.size() + " of " + inputs.size() + " inputs");
        System.out.println("Time using validation: " + elapsedMs + " ms");
        System.out.println("Should be dramatically faster than ExceptionAsControlFlowSlow.java");
    }
}
