// Association: one class uses another. The relationship is incidental —
// no ownership, no lifecycle coupling. EmailSender doesn't hold onto
// EmailMessages; it just processes them.

class EmailMessage {
    String to, body;
    EmailMessage(String to, String body) { this.to = to; this.body = body; }
}

class EmailSender {
    void send(EmailMessage msg) {
        System.out.println("Sending to " + msg.to + ": " + msg.body);
    }
}

public class Association {
    public static void main(String[] args) {
        EmailSender sender = new EmailSender();
        EmailMessage m1 = new EmailMessage("alice@example.com", "Welcome");
        EmailMessage m2 = new EmailMessage("bob@example.com",   "Reminder");

        sender.send(m1);
        sender.send(m2);

        // Neither object owns the other. The sender forgets m1 the moment
        // send() returns.
    }
}
