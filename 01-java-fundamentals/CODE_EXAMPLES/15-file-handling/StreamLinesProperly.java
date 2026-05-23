// Line-by-line reading for files too big to load entirely. BufferedReader
// reads chunks into an in-memory buffer; readLine() pulls one line at a time.
// Wrapped in try-with-resources so the reader closes automatically.

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StreamLinesProperly {
    public static void main(String[] args) throws IOException {

        Path path = Path.of("demo-big.txt");

        // Create a "big" file (1000 lines).
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 1000; i++) sb.append("line ").append(i).append("\n");
        Files.writeString(path, sb.toString());

        // Stream it line by line. Only one line is in memory at a time.
        int count = 0;
        long totalLength = 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                totalLength += line.length();
            }
        }   // reader.close() called automatically here

        System.out.println("lines:       " + count);
        System.out.println("total chars: " + totalLength);

        Files.delete(path);
    }
}
