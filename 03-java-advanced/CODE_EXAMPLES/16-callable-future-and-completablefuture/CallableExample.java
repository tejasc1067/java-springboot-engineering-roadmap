import java.util.concurrent.Callable;

public class CallableExample {

    public static void main(String[] args)
            throws Exception {

        Callable<String> callableTask =
                () -> "Async Result Returned";

        System.out.println(
                callableTask.call()
        );
    }
}