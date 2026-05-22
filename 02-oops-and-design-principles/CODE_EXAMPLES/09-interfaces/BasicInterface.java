// Define a Speaker contract; two classes commit to it. Calling code works
// against Speaker — it never has to know which concrete class is behind it.

interface Speaker {
    void speak();
}

class Dog implements Speaker {
    @Override
    public void speak() {
        System.out.println("Woof");
    }
}

class Robot implements Speaker {
    @Override
    public void speak() {
        System.out.println("BEEP BOOP");
    }
}

public class BasicInterface {
    public static void main(String[] args) {
        Speaker[] speakers = { new Dog(), new Robot() };
        for (Speaker s : speakers) {
            s.speak();
        }
        // Caller treats them uniformly; each speaks in its own way.
    }
}
