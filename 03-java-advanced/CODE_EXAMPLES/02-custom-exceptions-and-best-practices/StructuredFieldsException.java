public class StructuredFieldsException {

    static class InsufficientFundsException extends RuntimeException {
        private final String accountId;
        private final double requested;
        private final double available;

        public InsufficientFundsException(String accountId, double requested, double available) {
            super(String.format("account %s: requested %.2f, only %.2f available",
                                accountId, requested, available));
            this.accountId = accountId;
            this.requested = requested;
            this.available = available;
        }

        public String getAccountId()  { return accountId; }
        public double getRequested()  { return requested; }
        public double getAvailable()  { return available; }
    }

    static void withdraw(String accountId, double amount, double balance) {
        if (amount > balance) {
            throw new InsufficientFundsException(accountId, amount, balance);
        }
    }

    public static void main(String[] args) {
        try {
            withdraw("A-123", 500.0, 120.50);
        } catch (InsufficientFundsException e) {
            System.out.println("log message: " + e.getMessage());
            System.out.println();
            System.out.println("structured JSON for client (no string parsing needed):");
            System.out.printf("  {%n");
            System.out.printf("    \"error\": \"insufficient_funds\",%n");
            System.out.printf("    \"account\": \"%s\",%n", e.getAccountId());
            System.out.printf("    \"requested\": %.2f,%n", e.getRequested());
            System.out.printf("    \"available\": %.2f%n", e.getAvailable());
            System.out.printf("  }%n");
        }
    }
}
