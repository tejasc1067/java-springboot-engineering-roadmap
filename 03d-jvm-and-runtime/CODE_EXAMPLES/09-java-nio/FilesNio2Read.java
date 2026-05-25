import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class FilesNio2Read {

    public static void main(String[] args) throws IOException {
        // Make a small temp file we control.
        Path tmp = Files.createTempFile("nio-demo-", ".txt");
        Files.writeString(tmp,
            "first line\nsecond line\nthird line\nfourth line\n");
        System.out.println("wrote " + tmp);

        // 1) readString -- whole file, single allocation. Good for small files.
        String all = Files.readString(tmp);
        System.out.println();
        System.out.println("readString:");
        System.out.print(all);

        // 2) readAllLines -- whole file as a List<String>. Good for small structured files.
        List<String> lines = Files.readAllLines(tmp);
        System.out.println();
        System.out.println("readAllLines: " + lines);

        // 3) Files.lines -- a Stream<String>, lazy. Constant memory regardless of file size.
        //    Must be closed (try-with-resources) because it holds the file open.
        System.out.println();
        System.out.println("Files.lines (streamed):");
        try (Stream<String> s = Files.lines(tmp)) {
            s.filter(line -> line.contains("second")).forEach(System.out::println);
        }

        // 4) Quick attributes.
        System.out.println();
        System.out.println("size: " + Files.size(tmp) + " bytes");

        Files.deleteIfExists(tmp);
    }
}
