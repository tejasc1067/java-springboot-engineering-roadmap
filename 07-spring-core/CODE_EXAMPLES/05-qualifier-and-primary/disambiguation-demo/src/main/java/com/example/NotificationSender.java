package com.example;

import org.springframework.stereotype.Service;

// No qualifier. Spring resolves to the @Primary bean (ConsoleEmailNotifier).
@Service
public class NotificationSender {

    private final EmailNotifier notifier;

    public NotificationSender(EmailNotifier notifier) {
        this.notifier = notifier;
    }

    public void send(String to, String message) {
        notifier.send(to, message);
    }

    EmailNotifier resolvedNotifier() {
        return notifier;
    }
}
