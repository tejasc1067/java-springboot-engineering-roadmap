import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Controller {
}

@Controller
class UserController {

    public void getUsers() {

        System.out.println(
                "Fetching Users"
        );
    }
}

public class BackendAnnotationWorkflowExample {

    public static void main(String[] args) {

        Class<UserController> clazz =
                UserController.class;

        if (clazz.isAnnotationPresent(Controller.class)) {

            System.out.println(
                    "Controller Detected"
            );
        }
    }
}