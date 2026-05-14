import java.util.ArrayList;
import java.util.List;

public class BackendMemoryPressureExample {

    public static void main(String[] args) {

        List<byte[]> requests =
                new ArrayList<>();

        for (int index = 0;
             index < 100; index++) {

            requests.add(
                    new byte[1024 * 1024]
            );

            System.out.println(
                    "Request Allocated Memory: "
                            + index
            );
        }
    }
}