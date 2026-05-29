package com.example;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// The fix: inject a factory for the prototype, not the prototype itself.
// Each tickets.getObject() call asks the container for a fresh WorkTicket.
@Component
public class FixedCoordinator {

    private final ObjectProvider<WorkTicket> tickets;

    public FixedCoordinator(ObjectProvider<WorkTicket> tickets) {
        this.tickets = tickets;
    }

    public String startWork() {
        WorkTicket fresh = tickets.getObject();
        return "starting with ticket " + fresh.id();
    }
}
