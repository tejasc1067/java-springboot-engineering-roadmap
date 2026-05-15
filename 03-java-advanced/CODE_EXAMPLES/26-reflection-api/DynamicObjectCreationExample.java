class Product {

    public Product() {

        System.out.println(
                "Product Object Created"
        );
    }
}

public class DynamicObjectCreationExample {

    public static void main(String[] args)
            throws Exception {

        Class<?> clazz =
                Product.class;

        Object object =
                clazz.getDeclaredConstructor()
                        .newInstance();

        System.out.println(object);
    }
}