import java.lang.reflect.Method;

class PaymentService {

    public void processPayment() {

        System.out.println(
                "Payment Processed"
        );
    }
}

public class MethodInspectionExample {

    public static void main(String[] args) {

        Class<PaymentService> clazz =
                PaymentService.class;

        Method[] methods =
                clazz.getDeclaredMethods();

        for (Method method : methods) {

            System.out.println(
                    method.getName()
            );
        }
    }
}