// The right way. The resource is declared inside `try (...)`. Java closes it
// when the block ends — whether by normal completion, an early return, or
// an exception. No leaks possible.

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TryWithResourcesProper {
    public static void main(String[] args) throws IOException {

        Path path = Path.of("demo-proper.txt");
        Files.writeString(path, "a\nb\nc\n");

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("read: " + line);
            }
        }   // reader.close() runs here, guaranteed

        // Multiple resources in one block — closed in REVERSE order.
        Path out = Path.of("demo-proper-out.txt");
        try (BufferedReader in = Files.newBufferedReader(path);
             var writer = Files.newBufferedWriter(out)) {
            String line;
            while ((line = in.readLine()) != null) {
                writer.write(line.toUpperCase());
                writer.newLine();
            }
        }
        System.out.println("\nupper-cased copy:");
        System.out.print(Files.readString(out));

        Files.delete(path);
        Files.delete(out);
    }
}
