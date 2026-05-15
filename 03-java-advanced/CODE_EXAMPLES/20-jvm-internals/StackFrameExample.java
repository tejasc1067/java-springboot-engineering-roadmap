public class StackFrameExample {

    public static void main(String[] args) {

        firstMethod();
    }

    public static void firstMethod() {

        secondMethod();
    }

    public static void secondMethod() {

        System.out.println(
                "Stack Frame Created"
        );
    }
}