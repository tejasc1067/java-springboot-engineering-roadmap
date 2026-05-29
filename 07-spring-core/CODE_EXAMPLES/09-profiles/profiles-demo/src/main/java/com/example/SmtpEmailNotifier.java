package com.example;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Not actually SMTP -- just prints to make the profile choice visible.
// In a real app this would call into an SMTP client library.
@Component
@Profile("prod")
public class SmtpEmailNotifier implements EmailNotifier {

    @Override
    public String send(String to, String message) {
        String line = "SMTP to=" + to + " msg=" + message;
        System.out.println(line);
        return line;
    }

    @Override
    public String channel() {
        return "smtp";
    }
}
