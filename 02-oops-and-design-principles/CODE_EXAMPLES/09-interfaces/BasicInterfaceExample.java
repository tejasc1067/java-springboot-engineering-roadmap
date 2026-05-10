interface Animal {

    void sound();
}

class Dog implements Animal {

    @Override
    public void sound() {

        System.out.println(
                "Dog Barks"
        );
    }
}

public class BasicInterfaceExample {

    public static void main(String[] args) {

        Dog dog = new Dog();

        dog.sound();
    }
}