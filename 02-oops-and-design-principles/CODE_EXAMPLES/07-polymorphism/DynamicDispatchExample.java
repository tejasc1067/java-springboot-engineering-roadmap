class Notification {

    void sendNotification() {

        System.out.println(
                "Sending Generic Notification"
        );
    }
}

class EmailNotification extends Notification {

    @Override
    void sendNotification() {

        System.out.println(
                "Sending Email Notification"
        );
    }
}

class SMSNotification extends Notification {

    @Override
    void sendNotification() {

        System.out.println(
                "Sending SMS Notification"
        );
    }
}

public class DynamicDispatchExample {

    public static void main(String[] args) {

        Notification notification;

        notification = new EmailNotification();

        notification.sendNotification();

        notification = new SMSNotification();

        notification.sendNotification();
    }
}