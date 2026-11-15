import java.util.List;

// Two exporters each copy the same "open, write, close" skeleton.
// One copy closes in a finally block; the other closes only on the happy path.
//
// COMPARE WITH: TemplateMethodPattern.java

class FakeFile {
    static int open = 0;
    FakeFile() { open++; }
    void write(String line) { if (line.contains("null")) throw new IllegalStateException("bad row: " + line); }
    void close() { open--; }
}

class CsvExporter {
    void export(List<String> rows) {
        FakeFile file = new FakeFile();
        file.write("id,name");
        for (String row : rows) file.write(row);
        file.close(); // skipped if a row throws
    }
}

class JsonExporter {
    void export(List<String> rows) {
        FakeFile file = new FakeFile();
        try {
            file.write("[");
            for (String row : rows) file.write("\"" + row + "\"");
            file.write("]");
        } finally {
            file.close();
        }
    }
}

public class CopiedSkeletonBroken {
    public static void main(String[] args) {
        List<String> rows = List.of("1,Ann", "2,null");
        try { new JsonExporter().export(rows); } catch (IllegalStateException e) { }
        System.out.println("Files left open after JSON export: " + FakeFile.open);
        try { new CsvExporter().export(rows); } catch (IllegalStateException e) { }
        System.out.println("Files left open after CSV export:  " + FakeFile.open);
        System.out.println("Same skeleton, copied twice, written correctly once. Each failed CSV export leaks a file handle.");
    }
}
