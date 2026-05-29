package com.example;

public class ConsoleEmailNotifier implements EmailNotifier {

    @Override
    public void send(String to, String message) {
        System.out.println("EMAIL to " + to + ": " + message);
    }
}
