// Files.writeString creates the file (or replaces it). Append mode requires
// an explicit StandardOpenOption.APPEND.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WriteAndAppend {
    public static void main(String[] args) throws IOException {

        Path path = Path.of("demo-output.txt");

        // First write — creates or replaces the file.
        Files.writeString(path, "first line\n");
        System.out.println("after first write:\n" + Files.readString(path));

        // Second writeString — REPLACES the file by default.
        Files.writeString(path, "REPLACED\n");
        System.out.println("after second write (replace):\n" + Files.readString(path));

        // To append, pass StandardOpenOption.APPEND.
        Files.writeString(path, "appended line 1\n", StandardOpenOption.APPEND);
        Files.writeString(path, "appended line 2\n", StandardOpenOption.APPEND);

        System.out.println("after appends:\n" + Files.readString(path));

        Files.delete(path);
    }
}
