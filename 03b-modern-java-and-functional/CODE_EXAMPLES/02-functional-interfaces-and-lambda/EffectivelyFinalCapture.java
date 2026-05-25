import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class EffectivelyFinalCapture {

    public static void main(String[] args) {
        // OK: prefix is assigned once, never reassigned -> effectively final.
        String prefix = "[INFO] ";
        Consumer<String> log = msg -> System.out.println(prefix + msg);
        log.accept("captured a String reference");

        // NOT OK: this would fail to compile.
        // int counter = 0;
        // Runnable bad = () -> counter++;     // local variable counter defined in an enclosing scope must be final or effectively final

        // Workaround 1: AtomicInteger -- the variable holds a reference (effectively final),
        // and we mutate via the held object.
        AtomicInteger counter = new AtomicInteger(0);
        Runnable good = counter::incrementAndGet;
        good.run(); good.run(); good.run();
        System.out.println("AtomicInteger workaround: counter = " + counter.get());

        // Workaround 2: a single-element array. Also legal (the array reference is final;
        // the element it contains is not).
        int[] box = { 0 };
        Runnable alsoGood = () -> box[0]++;
        alsoGood.run(); alsoGood.run();
        System.out.println("int[] workaround:         box[0] = " + box[0]);

        // Subtle trap: capturing a reference to a mutable object FREEZES the reference,
        // not the object. The lambda can still mutate what's inside.
        List<String> items = new java.util.ArrayList<>();
        items.add("a"); items.add("b");
        Runnable wipe = items::clear;     // items reference is effectively final; the list is not
        wipe.run();
        System.out.println("items after wipe:         " + items);
    }
}
