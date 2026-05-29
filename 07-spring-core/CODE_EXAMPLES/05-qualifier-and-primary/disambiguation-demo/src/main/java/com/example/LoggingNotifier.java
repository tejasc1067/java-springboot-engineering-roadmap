package com.example;

import org.springframework.stereotype.Component;

// Bean name defaults to "loggingNotifier" (class name, first letter lowercased).
// Consumers grab it with @Qualifier("loggingNotifier").
@Component
public class LoggingNotifier implements EmailNotifier {

    @Override
    public void send(String to, String message) {
        System.out.println("LOG  to=" + to + " msg=" + message);
    }
}
