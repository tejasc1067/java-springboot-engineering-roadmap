// The silent-swallow antipattern. The bug just disappears. No log, no stack,
// no clue. Hours of "why isn't this working" debugging start here.
//
// See LogAndRethrowProper.java for what to do instead.

public class EmptyCatchBroken {

    public static void main(String[] args) {

        int balance = chargeUser(100);     // pretends to succeed, returns 0

        System.out.println("balance after charge: " + balance);
        System.out.println("(notice: no error message, no warning, no clue what went wrong)");
    }

    static int chargeUser(int amount) {
        try {
            return processCharge(amount);
        } catch (Exception e) {
            // Silently swallowed. Don't do this.
        }
        return 0;
    }

    static int processCharge(int amount) {
        throw new RuntimeException("payment processor unreachable");
    }
}
