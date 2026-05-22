// Subclass replaces a method inherited from the parent.
// Resolved at runtime based on the actual object — not the variable type.

class Animal {
    void speak() {
        System.out.println("generic animal sound");
    }
}

class Dog extends Animal {
    @Override
    void speak() {
        System.out.println("Woof");
    }
}

class Cat extends Animal {
    @Override
    void speak() {
        System.out.println("Meow");
    }
}

public class MethodOverriding {
    public static void main(String[] args) {
        Animal a1 = new Dog();
        Animal a2 = new Cat();
        Animal a3 = new Animal();

        // All three variables are TYPED as Animal, but the JVM dispatches each
        // call to the method of the actual underlying object's class.
        a1.speak();    // Woof
        a2.speak();    // Meow
        a3.speak();    // generic animal sound

        // This is runtime polymorphism — the core mechanism behind topic 07.
    }
}
