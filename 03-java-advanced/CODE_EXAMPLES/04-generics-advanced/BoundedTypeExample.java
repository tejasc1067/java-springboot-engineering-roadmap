class NumberBox<T extends Number> {

    private final T value;

    NumberBox(T value) {

        this.value = value;
    }

    void printValue() {

        System.out.println(
                "Value: " + value
        );
    }
}

public class BoundedTypeExample {

    public static void main(String[] args) {

        NumberBox<Integer> integerBox =
                new NumberBox<>(100);

        NumberBox<Double> doubleBox =
                new NumberBox<>(99.99);

        integerBox.printValue();

        doubleBox.printValue();
    }
}