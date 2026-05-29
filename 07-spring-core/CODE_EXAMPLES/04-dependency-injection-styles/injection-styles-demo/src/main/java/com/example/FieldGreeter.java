package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// ANTIPATTERN.
// - formatter is set via reflection into a private field; no constructor, no setter.
// - cannot be final (assigned after construction).
// - `new FieldGreeter()` produces an object that NPEs on first use.
// - unit tests must use reflection or boot Spring -- there is no plain-Java path.
@Service
public class FieldGreeter {

    @Autowired
    private MessageFormatter formatter;

    public String greet(String name) {
        return formatter.format("Hello, " + name);
    }
}
