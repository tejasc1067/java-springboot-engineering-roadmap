// `throw` raises an exception.
// `throws` declares (on a method) that it might propagate certain checked
// exceptions, so the compiler can force callers to handle them.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThrowAndThrows {

    public static void main(String[] args) {

        // Throwing a runtime exception from your own validation.
        try {
            setAge(-5);
        } catch (IllegalArgumentException e) {
            System.out.println("blocked: " + e.getMessage());
        }

        // Calling a method that declares a checked exception.
        try {
            String contents = readFile("does-not-exist.txt");
            System.out.println(contents);
        } catch (IOException e) {
            System.out.println("read failed: " + e.getMessage());
        }
    }

    // Throwing IllegalArgumentException — it's unchecked, no `throws` needed.
    static void setAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age cannot be negative: " + age);
        }
    }

    // Files.readString throws IOException, which is CHECKED — we must either
    // catch it here, or declare it on our signature like this:
    static String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
