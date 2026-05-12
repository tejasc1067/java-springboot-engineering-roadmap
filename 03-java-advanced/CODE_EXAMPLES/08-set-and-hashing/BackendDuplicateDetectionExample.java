import java.util.HashSet;
import java.util.Set;

public class BackendDuplicateDetectionExample {

    public static void main(String[] args) {

        Set<String> processedTransactions =
                new HashSet<>();

        processedTransactions.add("TXN-1001");

        processedTransactions.add("TXN-1002");

        String transaction =
                "TXN-1001";

        if (processedTransactions.contains(transaction)) {

            System.out.println(
                    "Duplicate Transaction Detected"
            );

        } else {

            processedTransactions.add(transaction);
        }
    }
}