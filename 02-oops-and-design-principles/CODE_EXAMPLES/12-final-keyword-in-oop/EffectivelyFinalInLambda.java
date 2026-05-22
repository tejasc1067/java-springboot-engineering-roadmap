// Lambdas can only capture local variables that are final or "effectively final"
// (never reassigned after their first assignment).

public class EffectivelyFinalInLambda {
    public static void main(String[] args) {

        // Case 1: effectively final — fine.
        String greeting = "Hello";
        Runnable r1 = () -> System.out.println(greeting + " world");
        r1.run();

        // Case 2: explicitly final — also fine.
        final String greeting2 = "Hi";
        Runnable r2 = () -> System.out.println(greeting2 + " there");
        r2.run();

        // Case 3: reassigning the captured variable breaks the rule.
        String name = "Alice";
        Runnable r3 = () -> System.out.println("Hello " + name);
        // name = "Bob";          // ← would make `name` no longer effectively final
                                  //   and break the lambda capture above; compile error.
        r3.run();

        System.out.println("\nLambdas need stable captured state — that's why the rule exists.");
    }
}
