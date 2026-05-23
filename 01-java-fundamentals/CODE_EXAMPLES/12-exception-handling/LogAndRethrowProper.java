// The corrected version of EmptyCatchBroken.java. Catch the specific type
// you can actually handle, log it with enough context, and either recover
// gracefully or rethrow.

public class LogAndRethrowProper {

    public static void main(String[] args) {

        // Option 1: callers handle the failure visibly.
        try {
            int balance = chargeUser(100);
            System.out.println("balance after charge: " + balance);
        } catch (RuntimeException e) {
            System.out.println("charge failed: " + e.getMessage());
            // Real apps would also send to a logger, alert on error rate, etc.
        }
    }

    static int chargeUser(int amount) {
        try {
            return processCharge(amount);
        } catch (RuntimeException e) {
            // Either: log here and rethrow,
            //   System.err.println("processCharge failed for amount " + amount + ": " + e);
            //   throw e;
            // Or: wrap with more context and rethrow.
            throw new RuntimeException("chargeUser(amount=" + amount + ") failed", e);
        }
    }

    static int processCharge(int amount) {
        throw new RuntimeException("payment processor unreachable");
    }
}
