# 15 — File Handling

Reading and writing files is one of those things you'll do constantly: parsing CSVs, reading config files, writing logs, dumping reports. Java's file APIs have evolved across versions, and the result is that there are several ways to do every operation. We'll pick one good way for each, and you'll see the older forms in legacy code.

The single most important habit: **close your file handles**. Java's `try-with-resources` makes that automatic — use it everywhere.

---

## The modern toolbox: `java.nio.file`

For most read/write operations, Java's `Files` class (in `java.nio.file`, added in Java 7+) does what you want in one or two lines.

```java
import java.nio.file.Files;
import java.nio.file.Path;

// Read the whole file into a String — perfect for small files (config, templates).
String text = Files.readString(Path.of("data.txt"));

// Read line by line into a List.
List<String> lines = Files.readAllLines(Path.of("data.txt"));

// Write a String to a file (creates or replaces).
Files.writeString(Path.of("out.txt"), "hello\n");

// Append.
Files.writeString(Path.of("out.txt"), "more\n", StandardOpenOption.APPEND);

// Existence checks.
boolean exists = Files.exists(Path.of("data.txt"));
long size = Files.size(Path.of("data.txt"));
```

These methods throw `IOException` (checked), so you'll need to either catch them or declare `throws IOException`.

---

## Streaming large files: BufferedReader and BufferedWriter

`readAllLines` is convenient but loads the whole file into memory. For files bigger than a few MB, stream them line by line.

```java
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;

try (BufferedReader reader = Files.newBufferedReader(Path.of("big.log"))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // process one line at a time
    }
}
```

Buffering is the key — without it, every `read()` would be a slow disk call. `BufferedReader` reads chunks and serves them from memory.

Writing:

```java
try (BufferedWriter writer = Files.newBufferedWriter(Path.of("out.txt"))) {
    writer.write("first line");
    writer.newLine();
    writer.write("second line");
    writer.newLine();
}
```

The writer flushes and closes when the try-with-resources block ends. If you don't close, partial buffers may never reach disk.

---

## try-with-resources

The block in `try (resource) { ... }` automatically calls `.close()` on the resource when the block ends — exceptional exit, return, anything. This is *the* idiomatic way to handle anything closeable: files, sockets, database connections, streams.

```java
try (BufferedReader r = Files.newBufferedReader(path)) {
    // use r
}    // r.close() called here, even if an exception was thrown
```

The old equivalent was:

```java
BufferedReader r = null;
try {
    r = ...;
    // use r
} finally {
    if (r != null) r.close();    // and what if .close() throws?
}
```

Easy to get wrong. Try-with-resources gets it right every time. Always use it.

You can declare multiple resources in one block:

```java
try (BufferedReader in = Files.newBufferedReader(src);
     BufferedWriter out = Files.newBufferedWriter(dst)) {
    String line;
    while ((line = in.readLine()) != null) {
        out.write(line);
        out.newLine();
    }
}
```

---

## Paths

`Path` is the modern way to represent a file location. Build one with `Path.of(...)`:

```java
Path p1 = Path.of("data.txt");                       // relative
Path p2 = Path.of("/var/log/app.log");               // absolute (Linux)
Path p3 = Path.of("C:", "Users", "alice", "x.txt");  // segments

p1.toAbsolutePath();      // resolve relative to current dir
p3.getFileName();         // x.txt
p3.getParent();           // C:\Users\alice
```

For URLs that aren't quite files (jar resources, web URLs), Java has a `URI` and a `URL` — different but related.

---

## Broken / proper patterns

**Broken — no close, no try-with-resources:**

```java
BufferedReader reader = Files.newBufferedReader(Path.of("data.txt"));
String line;
while ((line = reader.readLine()) != null) {
    process(line);
}
// reader is never closed — leaks an OS-level file handle.
// If process() throws, we double-leak (the exception unwinds, and we still don't close).
```

**Proper:**

```java
try (BufferedReader reader = Files.newBufferedReader(Path.of("data.txt"))) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}
```

The leak isn't visible on a small demo — modern OSes will collect the handle eventually. But in a long-running web server, you'll exhaust the OS's file-handle limit in days.

**Broken — overwriting valuable data accidentally:**

```java
Files.writeString(Path.of("important.txt"), "x");   // silently replaces existing file
```

If you didn't mean to overwrite, use `StandardOpenOption.CREATE_NEW` — it throws if the file already exists.

```java
Files.writeString(Path.of("important.txt"), "x", StandardOpenOption.CREATE_NEW);
```

---

## Common pitfalls

- **Hardcoded absolute paths.** `C:\\Users\\alice\\data.txt` works on your machine, not on a teammate's, not on a Linux server. Use relative paths or read paths from config.
- **Reading a giant file into memory with `readAllLines`.** Fine for 1 MB, terrible for 1 GB. Use `BufferedReader` and stream.
- **Forgetting to flush a writer.** Without `flush()` or `close()`, buffered output may never reach disk. Try-with-resources solves it.
- **Not handling `IOException`.** Compiler forces you to catch or declare; people sometimes catch and swallow. At minimum, log the error.
- **Building paths by string concatenation.** `"data" + "/" + "x.txt"` works until someone runs on Windows. Use `Path.of("data", "x.txt")` — Java picks the right separator.

---

## Code examples

1. `ReadWholeFile.java` — `Files.readString` and `Files.readAllLines` for small files.
2. `StreamLinesProperly.java` — line-by-line read with `BufferedReader`, wrapped in try-with-resources.
3. `WriteAndAppend.java` — `Files.writeString` to create/overwrite, then append.
4. `ResourceLeakBroken.java` paired with `TryWithResourcesProper.java` — explicit broken version (no close) and the corrected version.

Each example creates and reads/writes a temporary file in the current directory; you can delete `demo-input.txt` and `demo-output.txt` afterward.

---

## Try this yourself

1. Run `WriteAndAppend.java`, then open `demo-output.txt` in a text editor and look at the result. Re-run it — confirm the file grew.
2. In `StreamLinesProperly.java`, throw a runtime exception from inside the loop. Confirm via a debugger or by adding a print to the close path that the reader still gets closed.
3. Modify `ReadWholeFile.java` to count lines without using `readAllLines` (line by line is more memory-efficient on big files).

---

## Self-check

1. Why is `try-with-resources` better than `try/finally { close(); }`?
2. You're parsing a 5 GB log file. Why is `Files.readAllLines` a bad idea, and what should you use instead?
3. A teammate writes `Path.of("C:\\Users\\alice\\data.txt")`. What's wrong with this even when running on Windows, and what's the more portable alternative?
