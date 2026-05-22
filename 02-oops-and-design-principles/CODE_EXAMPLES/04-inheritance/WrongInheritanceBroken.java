// Classic bad inheritance. "I need a stack. ArrayList already has push-like
// methods. I'll extend it." Now the stack inherits every ArrayList method —
// including operations that violate stack semantics (random-index access,
// inserting in the middle, removing from the middle).
//
// A user of MyStack can corrupt it without going through push/pop.

import java.util.ArrayList;

class MyStack<T> extends ArrayList<T> {

    public void push(T item) {
        add(item);
    }

    public T pop() {
        if (isEmpty()) throw new IllegalStateException("empty");
        return remove(size() - 1);
    }

    public T peek() {
        if (isEmpty()) throw new IllegalStateException("empty");
        return get(size() - 1);
    }
}

public class WrongInheritanceBroken {
    public static void main(String[] args) {
        MyStack<String> stack = new MyStack<>();
        stack.push("a");
        stack.push("b");
        stack.push("c");
        System.out.println("Stack contents: " + stack);   // [a, b, c]

        // None of these should be possible on a stack — but they are, because
        // MyStack inherited every ArrayList method.
        stack.add(0, "X");                 // insert at front
        stack.remove(1);                    // remove from middle
        System.out.println("After ArrayList-level mutation: " + stack);

        // pop() now returns something unexpected, because the underlying list
        // was reshuffled outside the push/pop contract.
        System.out.println("pop(): " + stack.pop());
        System.out.println("Stack is corrupted because inheritance leaked too much surface.");
        System.out.println("See CompositionFixed.java for the right approach.");
    }
}
