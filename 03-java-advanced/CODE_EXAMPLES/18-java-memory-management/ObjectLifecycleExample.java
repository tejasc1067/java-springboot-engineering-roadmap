class Product {

    Product() {

        System.out.println(
                "Object Created"
        );
    }
}

public class ObjectLifecycleExample {

    public static void main(String[] args) {

        Product product =
                new Product();

        product = null;

        System.gc();

        System.out.println(
                "Object Eligible For GC"
        );
    }
}