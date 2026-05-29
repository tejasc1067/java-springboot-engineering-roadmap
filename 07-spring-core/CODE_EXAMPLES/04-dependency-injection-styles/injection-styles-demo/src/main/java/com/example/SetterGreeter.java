package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// LEGACY.
// - formatter cannot be final; the setter assigns it after construction.
// - between `new SetterGreeter()` and `setFormatter(...)`, the object is invalid.
// - @Autowired is REQUIRED on the setter; forgetting it gives a silent null.
@Service
public class SetterGreeter {

    private MessageFormatter formatter;

    @Autowired
    public void setFormatter(MessageFormatter formatter) {
        this.formatter = formatter;
    }

    public String greet(String name) {
        return formatter.format("Hello, " + name);
    }
}
