// `instanceof` checks the actual object type. Cast lets you call subclass-only
// methods after confirming the type. Java 16+ has pattern-matching for instanceof
// that combines both steps.
//
// Note: needing this is usually a sign of weak polymorphism. The fix is to
// move the subtype-specific behavior up to the common type (so the cast
// becomes unnecessary).

class Animal {
    String name;
    Animal(String name) { this.name = name; }
}

class Dog extends Animal {
    Dog(String name) { super(name); }
    void bark() { System.out.println(name + " barks"); }
}

class Cat extends Animal {
    Cat(String name) { super(name); }
    void purr() { System.out.println(name + " purrs"); }
}

public class InstanceOfAndCasting {

    public static void main(String[] args) {
        Animal[] zoo = { new Dog("Rex"), new Cat("Whiskers"), new Animal("Generic") };

        for (Animal a : zoo) {
            // Pre-Java-16 style:
            if (a instanceof Dog) {
                Dog d = (Dog) a;
                d.bark();
            } else if (a instanceof Cat) {
                Cat c = (Cat) a;
                c.purr();
            } else {
                System.out.println(a.name + " does generic animal stuff");
            }
        }

        System.out.println();

        // Java 16+ pattern-matching: declare the variable inside the check.
        for (Animal a : zoo) {
            if (a instanceof Dog d) {
                d.bark();
            } else if (a instanceof Cat c) {
                c.purr();
            } else {
                System.out.println(a.name + " does generic animal stuff");
            }
        }

        // Be careful: casting without checking can throw ClassCastException.
        try {
            Dog rex = (Dog) zoo[1];   // zoo[1] is actually a Cat
        } catch (ClassCastException e) {
            System.out.println("\nCaught CCE: " + e.getMessage());
        }
    }
}
