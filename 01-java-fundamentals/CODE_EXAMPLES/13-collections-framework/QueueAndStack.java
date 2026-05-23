// ArrayDeque can be used as a queue (FIFO) or a stack (LIFO). It's the
// modern default for both — faster than LinkedList, more flexible than the
// legacy Stack class.

import java.util.ArrayDeque;
import java.util.Deque;

public class QueueAndStack {
    public static void main(String[] args) {

        // FIFO queue
        Deque<String> queue = new ArrayDeque<>();
        queue.offer("a");
        queue.offer("b");
        queue.offer("c");

        System.out.println("queue: " + queue);
        System.out.println("poll: " + queue.poll());      // "a"
        System.out.println("poll: " + queue.poll());      // "b"
        System.out.println("remaining: " + queue);

        // LIFO stack
        Deque<String> stack = new ArrayDeque<>();
        stack.push("a");
        stack.push("b");
        stack.push("c");

        System.out.println("\nstack: " + stack + "   (top is 'c')");
        System.out.println("pop: " + stack.pop());        // "c"
        System.out.println("pop: " + stack.pop());        // "b"
        System.out.println("remaining: " + stack);
    }
}
