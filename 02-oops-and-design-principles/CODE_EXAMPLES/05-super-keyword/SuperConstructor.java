// Animal has no no-arg constructor, so Dog MUST call super(name) explicitly.
// Without it, Dog wouldn't compile.

class Animal {
    String name;

    Animal(String name) {
        this.name = name;
        System.out.println("  Animal(" + name + ") ran");
    }
}

class Dog extends Animal {
    String breed;

    Dog(String name, String breed) {
        super(name);          // must be the FIRST statement
        this.breed = breed;
        System.out.println("  Dog(" + name + ", " + breed + ") ran");
    }
}

public class SuperConstructor {
    public static void main(String[] args) {
        new Dog("Rex", "Labrador");
        // Output traces both constructors in order:
        //   Animal(Rex) ran
        //   Dog(Rex, Labrador) ran
    }
}
