import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Service {

    String name();
}

@Service(name = "PaymentService")
class PaymentService {
}

public class RuntimeAnnotationInspectionExample {

    public static void main(String[] args) {

        Class<PaymentService> clazz =
                PaymentService.class;

        if (clazz.isAnnotationPresent(Service.class)) {

            Service service =
                    clazz.getAnnotation(Service.class);

            System.out.println(
                    service.name()
            );
        }
    }
}