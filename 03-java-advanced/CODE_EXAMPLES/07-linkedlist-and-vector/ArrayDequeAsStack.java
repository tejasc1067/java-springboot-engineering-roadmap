import java.util.ArrayDeque;
import java.util.Deque;

public class ArrayDequeAsStack {

    public static void main(String[] args) {
        Deque<String> stack = new ArrayDeque<>();

        stack.push("first");
        stack.push("second");
        stack.push("third");

        System.out.println("stack: " + stack + "   (leftmost = top)");
        System.out.println("peek:  " + stack.peek());

        while (!stack.isEmpty()) {
            System.out.println("pop:   " + stack.pop());
        }

        System.out.println();
        System.out.println("Deque.push/pop is the modern stack pattern.");
        System.out.println("java.util.Stack (lowercase) is legacy -- use ArrayDeque instead.");
    }
}
