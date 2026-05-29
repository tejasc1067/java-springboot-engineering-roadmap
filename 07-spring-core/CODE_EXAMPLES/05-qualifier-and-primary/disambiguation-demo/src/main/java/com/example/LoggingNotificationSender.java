package com.example;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

// Explicit pick. @Qualifier names the bean to inject, overriding @Primary.
@Service
public class LoggingNotificationSender {

    private final EmailNotifier notifier;

    public LoggingNotificationSender(@Qualifier("loggingNotifier") EmailNotifier notifier) {
        this.notifier = notifier;
    }

    public void send(String to, String message) {
        notifier.send(to, message);
    }

    EmailNotifier resolvedNotifier() {
        return notifier;
    }
}
