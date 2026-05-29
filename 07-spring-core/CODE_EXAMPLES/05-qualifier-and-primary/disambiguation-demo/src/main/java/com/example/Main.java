package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("-- NotificationSender (no qualifier; gets @Primary) --");
            context.getBean(NotificationSender.class).send("ada@example.com", "hello");

            System.out.println("-- LoggingNotificationSender (@Qualifier picks the logging bean) --");
            context.getBean(LoggingNotificationSender.class).send("ada@example.com", "hello");

            System.out.println("-- BroadcastSender (List<EmailNotifier> -- both fire) --");
            context.getBean(BroadcastSender.class).announce("ada@example.com", "hello");
        }
    }
}
