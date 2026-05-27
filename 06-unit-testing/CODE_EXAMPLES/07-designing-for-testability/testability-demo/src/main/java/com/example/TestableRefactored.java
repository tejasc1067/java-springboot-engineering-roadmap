package com.example;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Same behavior as HardToTestBroken, but its two dependencies now come IN through the
 * constructor instead of being created inside:
 *
 *   - the MessageGateway (so a test can pass a fake instead of the real SMTP one)
 *   - a java.time.Clock (so a test can fix "today" to any date it likes)
 *
 * The class no longer decides what gateway to use or what day it is. The caller does -
 * and in a test, the caller is the test. This single change (constructor injection) is
 * what makes the class testable, and it's exactly the idea Spring's dependency injection
 * automates for you in module 07.
 */
public class TestableRefactored {

    private final MessageGateway gateway;
    private final Clock clock;

    public TestableRefactored(MessageGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public boolean sendDueReminder(String user) {
        LocalDate today = LocalDate.now(clock);   // reads the injected clock, not the system one

        if (today.getDayOfMonth() == 1) {
            return gateway.send(user, "Your monthly payment is due.");
        }
        return false;
    }
}
