package com.example;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingPrinter {

    private final String message;

    public GreetingPrinter(@Value("${app.message:Packaged successfully.}") String message) {
        this.message = message;
    }

    @PostConstruct
    void announce() {
        System.out.println("[packaging-demo] " + message);
    }

    public String message() {
        return message;
    }
}
