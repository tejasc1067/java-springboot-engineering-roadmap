package com.example;

// No annotation -- interfaces are not beans; their implementations are.
public interface EmailNotifier {
    void send(String to, String message);
}
