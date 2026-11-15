import java.util.List;

// Template method: the base class owns the skeleton (open, try, close) in a final method.
// Subclasses fill in only the steps that differ. Nobody can forget the finally block,
// because nobody writes it but the base class.
//
// COMPARE WITH: CopiedSkeletonBroken.java

class FakeFile {
    static int open = 0;
    FakeFile() { open++; }
    void write(String line) { if (line.contains("null")) throw new IllegalStateException("bad row: " + line); }
    void close() { open--; }
}

abstract class Exporter {
    // final: subclasses can't change the order or drop the close.
    final void export(List<String> rows) {
        FakeFile file = new FakeFile();
        try {
            file.write(header());
            for (String row : rows) file.write(formatRow(row));
            String footer = footer();
            if (footer != null) file.write(footer);
        } finally {
            file.close();
        }
    }

    abstract String header();
    abstract String formatRow(String row);
    String footer() { return null; } // a "hook": optional step with a default
}

class CsvExporter extends Exporter {
    String header() { return "id,name"; }
    String formatRow(String row) { return row; }
}

class JsonExporter extends Exporter {
    String header() { return "["; }
    String formatRow(String row) { return "\"" + row + "\""; }
    @Override String footer() { return "]"; }
}

public class TemplateMethodPattern {
    public static void main(String[] args) {
        List<String> rows = List.of("1,Ann", "2,null");
        for (Exporter exporter : List.of(new CsvExporter(), new JsonExporter())) {
            try { exporter.export(rows); } catch (IllegalStateException e) { }
        }
        System.out.println("Files left open after both failed exports: " + FakeFile.open);
        if (FakeFile.open != 0) throw new AssertionError("leaked " + FakeFile.open);
    }
}
