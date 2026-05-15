import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface BackendComponent {

    String value();
}

@BackendComponent(
        value = "UserService"
)
class UserService {
}

public class CustomAnnotationExample {

    public static void main(String[] args) {

        System.out.println(
                "Custom Annotation Created"
        );
    }
}