import java.util.LinkedList;
import java.util.List;

public class LinkedListInsertionExample {

    public static void main(String[] args) {

        List<String> tasks =
                new LinkedList<>();

        tasks.add("Task-1");

        tasks.add("Task-3");

        tasks.add(1, "Task-2");

        System.out.println(tasks);
    }
}