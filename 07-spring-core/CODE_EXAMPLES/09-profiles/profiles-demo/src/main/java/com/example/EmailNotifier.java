package com.example;

public interface EmailNotifier {
    String send(String to, String message);
    String channel();
}
