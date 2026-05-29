package com.example;

import org.springframework.stereotype.Service;

// RECOMMENDED.
// - formatter is final -> can't be reassigned, can't be null after construction.
// - single constructor -> Spring uses it automatically, no @Autowired needed.
// - plain Java tests can construct this with `new`.
@Service
public class ConstructorGreeter {

    private final MessageFormatter formatter;

    public ConstructorGreeter(MessageFormatter formatter) {
        this.formatter = formatter;
    }

    public String greet(String name) {
        return formatter.format("Hello, " + name);
    }
}
