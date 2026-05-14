public class StackOverflowExample {

    public static void recursiveMethod() {

        recursiveMethod();
    }

    public static void main(String[] args) {

        recursiveMethod();
    }
}