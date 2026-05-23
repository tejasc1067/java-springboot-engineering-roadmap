import java.util.ArrayDeque;
import java.util.Queue;

public class ArrayDequeAsQueue {

    public static void main(String[] args) {
        Queue<String> queue = new ArrayDeque<>();

        queue.offer("task-1");
        queue.offer("task-2");
        queue.offer("task-3");

        System.out.println("queue: " + queue);
        System.out.println("peek (front): " + queue.peek());

        while (!queue.isEmpty()) {
            System.out.println("poll: " + queue.poll());
        }

        System.out.println();
        System.out.println("FIFO via offer/poll/peek. Same Deque interface — switch to a different");
        System.out.println("implementation (LinkedList, PriorityQueue) by changing one line.");
    }
}
