import java.util.ArrayList;
import java.util.List;

public class BatchProcessingExample {

    public static void main(String[] args) {

        List<String> records =
                new ArrayList<>();

        for (int index = 1;
             index <= 10;
             index++) {

            records.add(
                    "Record-" + index
            );
        }

        int batchSize = 3;

        for (int index = 0;
             index < records.size();
             index += batchSize) {

            int end =
                    Math.min(
                            index + batchSize,
                            records.size()
                    );

            List<String> batch =
                    records.subList(index, end);

            System.out.println(
                    "Processing Batch: "
                            + batch
            );
        }
    }
}