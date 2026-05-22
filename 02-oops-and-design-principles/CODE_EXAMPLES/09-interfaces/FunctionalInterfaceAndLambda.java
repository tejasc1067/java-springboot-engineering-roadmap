// A functional interface has exactly ONE abstract method. You can implement
// it with a lambda expression — no class needed.
//
// This is the foundation of streams, callbacks, and modern Java functional
// style (covered in detail in module 03).

@FunctionalInterface
interface Greeter {
    String greet(String name);
}

@FunctionalInterface
interface Calculator {
    int compute(int a, int b);
}

public class FunctionalInterfaceAndLambda {
    public static void main(String[] args) {

        // Old way: anonymous class.
        Greeter formal = new Greeter() {
            @Override
            public String greet(String name) {
                return "Hello, " + name;
            }
        };
        System.out.println(formal.greet("Alice"));

        // New way: lambda. Same behavior, much less ceremony.
        Greeter casual = name -> "Hey " + name + "!";
        System.out.println(casual.greet("Bob"));

        // Calculator: pick the operation at the call site.
        Calculator add = (a, b) -> a + b;
        Calculator mul = (a, b) -> a * b;
        System.out.println("3 + 4 = " + add.compute(3, 4));
        System.out.println("3 * 4 = " + mul.compute(3, 4));

        // Lambdas are the runtime form of a functional interface's single
        // abstract method. Wherever a Calculator is expected, you can pass
        // a lambda that matches its signature.
    }
}
