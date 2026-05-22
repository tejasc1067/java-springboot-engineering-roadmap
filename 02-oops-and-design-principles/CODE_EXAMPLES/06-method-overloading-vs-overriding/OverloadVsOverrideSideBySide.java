// Both mechanisms in one file. Animal has overloaded speak() methods.
// Dog overrides one of them AND adds a new overload.

class Animal {
    void speak() {
        System.out.println("animal sound");
    }
    void speak(String detail) {                           // overload of speak()
        System.out.println("animal sound with " + detail);
    }
}

class Dog extends Animal {
    @Override
    void speak() {                                        // overrides Animal.speak()
        System.out.println("woof");
    }
    void speak(int times) {                               // new overload (Dog only)
        for (int i = 0; i < times; i++) speak();
    }
}

public class OverloadVsOverrideSideBySide {
    public static void main(String[] args) {
        Dog d = new Dog();

        d.speak();                  // "woof"          — overridden version
        d.speak("growl");           // "animal sound with growl" — inherited overload
        d.speak(3);                 // three "woof"s   — Dog-only overload that recursively calls speak()

        // The two mechanisms compose: speak(int) internally calls speak(),
        // which is overridden to print "woof".
    }
}
