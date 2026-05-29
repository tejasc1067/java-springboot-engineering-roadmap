package com.example;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class ConsoleEmailNotifier implements EmailNotifier {

    @Override
    public String send(String to, String message) {
        String line = "CONSOLE to=" + to + " msg=" + message;
        System.out.println(line);
        return line;
    }

    @Override
    public String channel() {
        return "console";
    }
}
