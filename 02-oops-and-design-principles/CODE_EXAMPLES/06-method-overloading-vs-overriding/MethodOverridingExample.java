class Animal {

    void sound() {

        System.out.println(
                "Animal Makes Sound"
        );
    }
}

class Dog extends Animal {

    @Override
    void sound() {

        System.out.println(
                "Dog Barks"
        );
    }
}

public class MethodOverridingExample {

    public static void main(String[] args) {

        Dog dog = new Dog();

        dog.sound();
    }
}