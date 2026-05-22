// Reference type: what the compiler sees. Object type: what runs at runtime.
// You can only call methods that exist on the reference type, even if the
// object actually has more.

class Animal {
    void speak() { System.out.println("animal sound"); }
}

class Dog extends Animal {
    @Override
    void speak() { System.out.println("Woof"); }
    void bark()  { System.out.println("specifically barking"); }   // Dog-only
}

public class ReferenceVsObjectType {
    public static void main(String[] args) {
        Animal a = new Dog();    // reference type: Animal; object type: Dog

        a.speak();               // compiles (Animal has speak); runs Dog.speak() — "Woof"
        // a.bark();             // ← won't compile: Animal doesn't know about bark()

        // To call Dog-specific methods, you need to cast (more in InstanceOfAndCasting).
        if (a instanceof Dog) {
            ((Dog) a).bark();
        }
    }
}
