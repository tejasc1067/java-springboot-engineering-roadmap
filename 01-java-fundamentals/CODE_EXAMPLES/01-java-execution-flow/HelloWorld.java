// The smallest legal Java program.
//
// Run it: java HelloWorld.java          (Java 11+, single-file source-code launch)
// Or:     javac HelloWorld.java && java HelloWorld
//
// Notice:
//   - The file is named HelloWorld.java and the public class is named HelloWorld.
//     That's not a coincidence — Java requires public class name == file name.
//   - The main method signature must be exactly:
//         public static void main(String[] args)
//     Anything else and the JVM won't find an entry point.

public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello from Java.");
    }
}
