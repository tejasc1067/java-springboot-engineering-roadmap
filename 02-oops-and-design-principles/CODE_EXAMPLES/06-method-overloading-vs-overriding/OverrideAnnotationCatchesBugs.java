// Why you should always use @Override.
//
// Without @Override, a typo in the method name silently creates a NEW method
// instead of overriding the parent's. The program compiles, but the override
// you wanted never happens. The bug is invisible at runtime — except for
// "why isn't my subclass behavior being used?"

class Animal {
    void speak() {
        System.out.println("animal sound");
    }
}

class DogWithoutAnnotation extends Animal {
    // Typo: capital S. This is a new method, not an override.
    // Without @Override the compiler accepts it.
    void Speak() {
        System.out.println("Woof");
    }
}

// Uncomment the annotation below to see the compile error catch the typo:
class DogWithAnnotation extends Animal {
    // @Override
    void Speak() {                  // typo — would error with @Override
        System.out.println("Woof");
    }

    @Override
    void speak() {                  // proper override; compiler verifies the parent has this method
        System.out.println("Woof (correct)");
    }
}

public class OverrideAnnotationCatchesBugs {
    public static void main(String[] args) {
        System.out.println("Without @Override (typo undetected):");
        new DogWithoutAnnotation().speak();    // prints "animal sound" — the parent's!

        System.out.println("\nWith proper override:");
        new DogWithAnnotation().speak();       // prints "Woof (correct)"
    }
}
