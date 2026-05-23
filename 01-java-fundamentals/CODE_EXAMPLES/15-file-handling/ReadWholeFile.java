// Two convenient ways to read a small file: as one big String, or as a List
// of lines. Use only for files that fit comfortably in memory.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReadWholeFile {
    public static void main(String[] args) throws IOException {

        Path path = Path.of("demo-input.txt");

        // Create a small file so the demo is self-contained.
        Files.writeString(path, "first line\nsecond line\nthird line\n");

        // Read the whole thing as one String.
        String text = Files.readString(path);
        System.out.println("---- as String ----");
        System.out.print(text);

        // Read line-by-line into a list.
        List<String> lines = Files.readAllLines(path);
        System.out.println("\n---- as List<String> ----");
        for (int i = 0; i < lines.size(); i++) {
            System.out.println((i + 1) + ": " + lines.get(i));
        }

        // Clean up after ourselves.
        Files.delete(path);
    }
}
