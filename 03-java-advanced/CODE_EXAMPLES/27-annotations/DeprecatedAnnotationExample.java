public class DeprecatedAnnotationExample {

    @Deprecated
    public void oldPaymentMethod() {

        System.out.println(
                "Old Payment Method"
        );
    }

    public static void main(String[] args) {

        DeprecatedAnnotationExample example =
                new DeprecatedAnnotationExample();

        example.oldPaymentMethod();
    }
}