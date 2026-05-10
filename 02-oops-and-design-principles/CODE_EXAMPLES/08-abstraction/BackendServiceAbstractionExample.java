abstract class NotificationService {

    abstract void sendNotification();
}

class EmailService extends NotificationService {

    @Override
    void sendNotification() {

        System.out.println(
                "Email Notification Sent"
        );
    }
}

class SMSService extends NotificationService {

    @Override
    void sendNotification() {

        System.out.println(
                "SMS Notification Sent"
        );
    }
}

public class BackendServiceAbstractionExample {

    public static void main(String[] args) {

        NotificationService notificationService;

        notificationService = new EmailService();

        notificationService.sendNotification();

        notificationService = new SMSService();

        notificationService.sendNotification();
    }
}