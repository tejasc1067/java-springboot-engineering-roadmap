import java.util.ArrayList;
import java.util.List;

public class MemoryLeakExample {

    private static final List<byte[]>
            cache =
            new ArrayList<>();

    public static void main(String[] args) {

        while (true) {

            cache.add(
                    new byte[1024 * 1024]
            );

            System.out.println(
                    "Objects Retained In Memory"
            );
        }
    }
}