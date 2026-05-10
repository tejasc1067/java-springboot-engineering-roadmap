class Animal {

    String type = "Animal";
}

class Dog extends Animal {

    String type = "Dog";

    void displayTypes() {

        System.out.println(
                "Child Type: "
                        + type
        );

        System.out.println(
                "Parent Type: "
                        + super.type
        );
    }
}

public class SuperVariableExample {

    public static void main(String[] args) {

        Dog dog = new Dog();

        dog.displayTypes();
    }
}