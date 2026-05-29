package com.example;

import org.springframework.stereotype.Component;

// The trap: singleton holding a prototype dependency.
// The constructor parameter is resolved ONCE, at this coordinator's construction.
// `ticket` stays the same object for the coordinator's entire life. Every call to
// startWork() returns the SAME ticket id, even though WorkTicket itself is prototype.
@Component
public class BrokenCoordinator {

    private final WorkTicket ticket;

    public BrokenCoordinator(WorkTicket ticket) {
        this.ticket = ticket;
    }

    public String startWork() {
        return "starting with ticket " + ticket.id();
    }
}
