import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteListenerRegistry {

    interface EventListener {
        void onEvent(String event);
    }

    public static void main(String[] args) {
        List<EventListener> listeners = new CopyOnWriteArrayList<>();

        listeners.add(e -> System.out.println("[listener-A] " + e));
        listeners.add(e -> System.out.println("[listener-B] " + e));

        Thread reader = new Thread(() -> {
            for (EventListener l : listeners) {
                l.onEvent("first-pass-event");
            }
        });

        Thread writer = new Thread(() -> {
            listeners.add(e -> System.out.println("[listener-C late] " + e));
        });

        reader.start();
        writer.start();
        try { reader.join(); writer.join(); } catch (InterruptedException ignored) {}

        System.out.println();
        System.out.println("now firing again -- listener-C is included this time:");
        for (EventListener l : listeners) {
            l.onEvent("second-pass-event");
        }
        System.out.println();
        System.out.println("the first reader's iteration was a snapshot -- no CME, no listener-C.");
        System.out.println("trade-off: every add() copied the array. Fine for rarely-changing registries.");
    }
}
