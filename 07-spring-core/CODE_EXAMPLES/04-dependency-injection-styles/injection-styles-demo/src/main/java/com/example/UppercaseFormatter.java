package com.example;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UppercaseFormatter implements MessageFormatter {

    @Override
    public String format(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}
