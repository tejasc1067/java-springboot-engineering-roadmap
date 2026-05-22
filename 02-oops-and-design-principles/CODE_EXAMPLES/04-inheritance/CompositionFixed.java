// Same goal as WrongInheritanceBroken, but using composition.
// MyStack now HAS an ArrayList instead of BEING one. The leaky methods are
// completely hidden — only push/pop/peek/size are exposed.

import java.util.ArrayList;
import java.util.List;

class MyStack<T> {
    private final List<T> items = new ArrayList<>();

    public void push(T item) {
        items.add(item);
    }

    public T pop() {
        if (items.isEmpty()) throw new IllegalStateException("empty");
        return items.remove(items.size() - 1);
    }

    public T peek() {
        if (items.isEmpty()) throw new IllegalStateException("empty");
        return items.get(items.size() - 1);
    }

    public int size() {
        return items.size();
    }

    @Override
    public String toString() {
        return items.toString();
    }
}

public class CompositionFixed {
    public static void main(String[] args) {
        MyStack<String> stack = new MyStack<>();
        stack.push("a");
        stack.push("b");
        stack.push("c");
        System.out.println("Stack: " + stack);
        System.out.println("peek: " + stack.peek());
        System.out.println("pop:  " + stack.pop());
        System.out.println("Stack: " + stack);

        // None of these compile — MyStack doesn't expose them.
        // stack.add(0, "X");
        // stack.remove(1);
        // stack.get(0);

        System.out.println("Composition exposes only what we want. Stack is uncorruptible from outside.");
    }
}
