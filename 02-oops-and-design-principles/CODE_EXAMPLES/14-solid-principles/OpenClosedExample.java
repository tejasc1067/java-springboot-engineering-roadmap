// Open/Closed: add new payment types without modifying existing code.
//
// VIOLATING version (commented at bottom): one big if/else block. Adding a
// new payment type means EDITING the processor — risking breakage to types
// that were already working.
//
// COMPLIANT version: an interface plus implementations. The processor calls
// the interface; new types add new classes, never touch existing ones.

interface PaymentMethod {
    void process(double amount);
}

class CardPayment implements PaymentMethod {
    public void process(double amount) { System.out.println("Charged $" + amount + " to card"); }
}

class PaypalPayment implements PaymentMethod {
    public void process(double amount) { System.out.println("PayPal sent $" + amount); }
}

class CryptoPayment implements PaymentMethod {
    public void process(double amount) { System.out.println("Crypto wallet transferred $" + amount); }
}

class PaymentProcessor {
    void process(PaymentMethod method, double amount) {
        method.process(amount);          // polymorphism; processor doesn't care which type
    }
}

public class OpenClosedExample {
    public static void main(String[] args) {
        PaymentProcessor p = new PaymentProcessor();
        p.process(new CardPayment(),   100);
        p.process(new PaypalPayment(),  50);
        p.process(new CryptoPayment(),  25);

        // To add ApplePay tomorrow:
        //   class ApplePayPayment implements PaymentMethod { ... }
        // PaymentProcessor doesn't change. No risk of breaking existing types.
    }

    // BAD VERSION (illustrative — don't write code like this):
    //
    // void process(String type, double amount) {
    //     if      (type.equals("card"))   { /* card logic */   }
    //     else if (type.equals("paypal")) { /* paypal logic */ }
    //     else if (type.equals("crypto")) { /* crypto logic */ }
    //     // Every new payment type FORCES an edit to this method.
    // }
}
