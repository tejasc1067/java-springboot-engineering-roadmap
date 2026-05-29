package com.example;

import org.springframework.stereotype.Component;

@Component
public class Mailer {

    private final EmailNotifier notifier;

    public Mailer(EmailNotifier notifier) {
        this.notifier = notifier;
    }

    public String send(String to, String message) {
        return notifier.send(to, message);
    }

    public String channel() {
        return notifier.channel();
    }
}
