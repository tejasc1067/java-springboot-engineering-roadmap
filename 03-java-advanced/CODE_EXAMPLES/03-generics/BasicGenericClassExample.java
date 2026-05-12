class Box<T> {

    private T data;

    void setData(T data) {

        this.data = data;
    }

    T getData() {

        return data;
    }
}

public class BasicGenericClassExample {

    public static void main(String[] args) {

        Box<String> box =
                new Box<>();

        box.setData("Java Generics");

        System.out.println(
                box.getData()
        );
    }
}