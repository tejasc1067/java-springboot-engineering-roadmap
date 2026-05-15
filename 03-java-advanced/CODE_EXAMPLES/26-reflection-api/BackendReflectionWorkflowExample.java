import java.lang.reflect.Method;

class UserService {

    public void getUsers() {

        System.out.println(
                "Fetching Users"
        );
    }
}

public class BackendReflectionWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        Class<UserService> clazz =
                UserService.class;

        Object service =
                clazz.getDeclaredConstructor()
                        .newInstance();

        Method method =
                clazz.getDeclaredMethod(
                        "getUsers"
                );

        method.invoke(service);
    }
}