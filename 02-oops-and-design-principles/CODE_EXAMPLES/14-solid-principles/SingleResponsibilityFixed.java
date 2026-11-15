// The same four jobs as SingleResponsibilityViolation.java, one class each.
// InvoiceWorkflow composes them; it changes only when the workflow itself changes.
//
// COMPARE WITH: SingleResponsibilityViolation.java

class Invoice {
    String customer;
    double amount;
    Invoice(String customer, double amount) { this.customer = customer; this.amount = amount; }
}

class InvoiceCalculator {            // changes when tax rules change
    double calculateTotal(Invoice inv, double taxRate) {
        return inv.amount * (1 + taxRate);
    }
}

class InvoicePdfRenderer {           // changes when the PDF library changes
    String render(Invoice inv) {
        return "PDF[" + inv.customer + ", $" + inv.amount + "]";
    }
}

class InvoiceEmailSender {           // changes when the SMTP provider changes
    void send(String pdf, String to) {
        System.out.println("Email to " + to + ": " + pdf);
    }
}

class InvoiceRepository {            // changes when the schema changes
    void save(Invoice inv) {
        System.out.println("INSERT INTO invoices VALUES ('" + inv.customer + "', " + inv.amount + ")");
    }
}

class InvoiceWorkflow {
    private final InvoiceCalculator calculator = new InvoiceCalculator();
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();
    private final InvoiceEmailSender emailSender = new InvoiceEmailSender();
    private final InvoiceRepository repository = new InvoiceRepository();

    void process(Invoice inv, String email) {
        System.out.println("Total: " + calculator.calculateTotal(inv, 0.10));
        emailSender.send(renderer.render(inv), email);
        repository.save(inv);
    }
}

public class SingleResponsibilityFixed {
    public static void main(String[] args) {
        new InvoiceWorkflow().process(new Invoice("Alice", 100.0), "alice@example.com");

        System.out.println("\nSwitching SMTP provider now touches InvoiceEmailSender only.");
        System.out.println("Tax rules, PDF rendering and the schema live in classes that never see that change.");
    }
}
