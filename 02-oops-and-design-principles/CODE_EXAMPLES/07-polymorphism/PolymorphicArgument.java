// Methods that take a parent type accept any subtype. New subtypes "just work"
// without modifying the method.

class Animal {
    String name;
    Animal(String name) { this.name = name; }
    void speak() { System.out.println(name + ": animal sound"); }
}

class Dog extends Animal {
    Dog(String name) { super(name); }
    @Override void speak() { System.out.println(name + ": Woof"); }
}

class Cat extends Animal {
    Cat(String name) { super(name); }
    @Override void speak() { System.out.println(name + ": Meow"); }
}

public class PolymorphicArgument {

    // This method doesn't care which Animal subclass is passed.
    static void feed(Animal a) {
        a.speak();
        System.out.println("  (eats food)");
    }

    public static void main(String[] args) {
        feed(new Dog("Rex"));         // works
        feed(new Cat("Whiskers"));    // works
        feed(new Animal("Generic"));  // works

        // The day a Bird class is added, feed() works for it too without modification.
        // This is the open/closed principle (topic 14 — the O in SOLID).
    }
}
