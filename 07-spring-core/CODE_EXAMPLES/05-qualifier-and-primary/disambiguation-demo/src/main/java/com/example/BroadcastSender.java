package com.example;

import org.springframework.stereotype.Service;

import java.util.List;

// All-of-them injection. Spring populates the list with every EmailNotifier bean.
@Service
public class BroadcastSender {

    private final List<EmailNotifier> notifiers;

    public BroadcastSender(List<EmailNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    public void announce(String to, String message) {
        for (EmailNotifier n : notifiers) {
            n.send(to, message);
        }
    }

    List<EmailNotifier> resolvedNotifiers() {
        return notifiers;
    }
}
