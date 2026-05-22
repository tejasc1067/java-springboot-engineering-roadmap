// Calling the parent's version of an overridden method.
// Common pattern: subclass extends the parent's behavior rather than replacing it.

class Animal {
    void describe() {
        System.out.println("I am an animal");
    }
}

class Dog extends Animal {
    @Override
    void describe() {
        super.describe();                                  // parent's behavior first
        System.out.println("Specifically, I am a dog");    // then our addition
    }
}

public class SuperMethod {
    public static void main(String[] args) {
        new Dog().describe();
        // I am an animal
        // Specifically, I am a dog

        // Without super.describe(), the Animal version would never run from
        // a Dog instance — overriding completely replaces by default.
    }
}
