package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessagePrinter {

    private final String message;

    public MessagePrinter(@Value("${app.message:Hello, World!}") String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
