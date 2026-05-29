package com.example;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Prototype: a new instance each time the container is asked for one.
// The unique id is the easiest way to see that two requests get different objects.
@Component
@Scope("prototype")
public class WorkTicket {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
