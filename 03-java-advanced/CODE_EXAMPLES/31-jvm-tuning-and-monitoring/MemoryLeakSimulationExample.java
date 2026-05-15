import java.util.ArrayList;
import java.util.List;

public class MemoryLeakSimulationExample {

    public static void main(String[] args) {

        List<byte[]> memoryLeak =
                new ArrayList<>();

        while (true) {

            memoryLeak.add(
                    new byte[1024 * 1024]
            );

            System.out.println(
                    "Objects Added: "
                            + memoryLeak.size()
            );
        }
    }
}