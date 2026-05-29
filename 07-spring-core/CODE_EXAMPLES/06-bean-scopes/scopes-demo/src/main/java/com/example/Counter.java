package com.example;

import org.springframework.stereotype.Component;

// Default scope is singleton. One instance, shared. State is shared across consumers.
@Component
public class Counter {

    private int value = 0;

    public int next() {
        return ++value;
    }

    public int current() {
        return value;
    }
}
