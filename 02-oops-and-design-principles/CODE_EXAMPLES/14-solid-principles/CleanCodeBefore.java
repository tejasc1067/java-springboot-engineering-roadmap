import java.util.List;

// Works, compiles, and has a bug nobody can see from the call site.
//
// COMPARE WITH: CleanCodeAfter.java

public class CleanCodeBefore {

    // calc total w/ 10% discount
    static double calc(List<double[]> l, boolean f) {
        double t = 0;
        for (double[] x : l) t += x[0] * x[1];   // x[0] = price? quantity? read the caller to find out
        if (f) t = t - t * 0.15;                  // the comment above says 10%. Which one is right?
        if (t > 100) t = t - 5;
        return t;
    }

    public static void main(String[] args) {
        List<double[]> items = List.of(new double[] {40.0, 2}, new double[] {25.0, 1});

        // The author of this line guessed that the flag meant "is guest".
        // It means "apply member discount". The guest just got 15% off.
        boolean isGuest = true;
        double guestTotal = calc(items, isGuest);

        System.out.println("Guest total: " + guestTotal + "   (expected 105.0 - 5.0 shipping credit = 100.0)");
        System.out.println("Nothing failed. The flag argument, the one-letter names and the stale");
        System.out.println("comment let a wrong call and a wrong rate both look reasonable.");
    }
}
