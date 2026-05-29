package com.example;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// @Primary says "when something asks for EmailNotifier and doesn't qualify, pick me."
@Component
@Primary
public class ConsoleEmailNotifier implements EmailNotifier {

    @Override
    public void send(String to, String message) {
        System.out.println("EMAIL to=" + to + " msg=" + message);
    }
}
