import java.util.Comparator;
import java.util.List;

public class StaticMethodOnInterface {

    interface Money {
        // Static factory on the interface -- no need for a separate Moneys utility class.
        static Money of(long cents) { return () -> cents; }
        long cents();
    }

    public static void main(String[] args) {
        Money a = Money.of(125);
        Money b = Money.of(99);
        System.out.println("a.cents() = " + a.cents());

        // Comparator.comparing is also a static method on the Comparator interface.
        List<Money> values = new java.util.ArrayList<>(List.of(a, b, Money.of(50)));
        values.sort(Comparator.comparingLong(Money::cents));
        for (Money m : values) System.out.println("sorted: " + m.cents());
    }
}
