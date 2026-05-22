// A class doing four jobs: calculate, render, email, persist.
// Each is a reason to change. The class will be modified for any of them.
//
// COMPARE WITH: SingleResponsibilityFixed.java

class Invoice {
    String customer;
    double amount;
    Invoice(String customer, double amount) { this.customer = customer; this.amount = amount; }
}

class InvoiceService {
    // 1. Business rules
    double calculateTotal(Invoice inv, double taxRate) {
        return inv.amount * (1 + taxRate);
    }

    // 2. Rendering (depends on a PDF library)
    String renderPdf(Invoice inv) {
        return "PDF[" + inv.customer + ", $" + inv.amount + "]";
    }

    // 3. Communication (depends on an SMTP gateway)
    void emailInvoice(Invoice inv, String to) {
        System.out.println("Email to " + to + ": " + renderPdf(inv));
    }

    // 4. Persistence (depends on a database)
    void saveToDatabase(Invoice inv) {
        System.out.println("INSERT INTO invoices VALUES ('" + inv.customer + "', " + inv.amount + ")");
    }
}

public class SingleResponsibilityViolation {
    public static void main(String[] args) {
        Invoice inv = new Invoice("Alice", 100.0);
        InvoiceService svc = new InvoiceService();
        System.out.println("Total: " + svc.calculateTotal(inv, 0.10));
        svc.emailInvoice(inv, "alice@example.com");
        svc.saveToDatabase(inv);

        System.out.println("\nThis one class will be modified when:");
        System.out.println("  - tax rules change");
        System.out.println("  - PDF library changes");
        System.out.println("  - SMTP provider changes");
        System.out.println("  - schema changes");
        System.out.println("Four reasons to change = SRP violation.");
    }
}
