// The wrong way. We open a BufferedReader but never close it. On a small demo
// the OS will collect the handle eventually; in a long-running server you'll
// exhaust the file-descriptor limit in days.
//
// See TryWithResourcesProper.java for the corrected version.

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceLeakBroken {
    public static void main(String[] args) throws IOException {

        Path path = Path.of("demo-leak.txt");
        Files.writeString(path, "a\nb\nc\n");

        BufferedReader reader = Files.newBufferedReader(path);
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("read: " + line);
        }
        // reader was never closed. Leak.
        // If readLine() above had thrown, we'd leak AND lose the exception
        // recovery path entirely.

        Files.delete(path);
    }
}
