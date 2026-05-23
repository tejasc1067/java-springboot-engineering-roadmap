public class BoxGenericClass {

    static class Box<T> {
        private T value;
        public void set(T value) { this.value = value; }
        public T get()           { return value; }
    }

    public static void main(String[] args) {
        Box<String> stringBox = new Box<>();
        stringBox.set("hello");
        String s = stringBox.get();
        System.out.println("string box: " + s);

        Box<Integer> intBox = new Box<>();
        intBox.set(42);
        int n = intBox.get();
        System.out.println("int box: " + n);

        Box<java.util.List<Double>> nestedBox = new Box<>();
        nestedBox.set(java.util.List.of(1.0, 2.0, 3.0));
        System.out.println("nested box: " + nestedBox.get());
    }
}
