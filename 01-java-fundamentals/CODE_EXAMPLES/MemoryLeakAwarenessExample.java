import java.util.ArrayList;
import java.util.List;

public class MemoryLeakAwarenessExample {

    // Static collection stays in memory longer
    static List<String> cache = new ArrayList<>();

    public static void main(String[] args) {

        for (int i = 0; i < 1000; i++) {

            cache.add("User-" + i);
        }

        System.out.println(
                "Cache Size: "
                        + cache.size()
        );

        System.out.println(
                "Improper static usage may increase memory usage"
        );
    }
}