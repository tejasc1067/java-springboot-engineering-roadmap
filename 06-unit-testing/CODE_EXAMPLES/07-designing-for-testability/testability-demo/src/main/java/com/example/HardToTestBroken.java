package com.example;

import java.time.LocalDate;

/**
 * This compiles and runs, but it cannot be cleanly unit-tested. Two reasons,
 * both about what it reaches for instead of what it is given:
 *
 *   1. It builds its own gateway with `new SmtpMessageGateway()`. A test has no way
 *      to substitute a fake, so the test would attempt a real send.
 *   2. It reads the real system clock via LocalDate.now(). A test can't control "today",
 *      so the "first of the month" branch fires only on the actual 1st - the test result
 *      depends on the calendar date you happen to run it.
 *
 * See HardToTestBrokenTest for the doomed test attempt (disabled), and
 * TestableRefactored for the fix.
 */
public class HardToTestBroken {

    public boolean sendDueReminder(String user) {
        MessageGateway gateway = new SmtpMessageGateway();   // problem 1
        LocalDate today = LocalDate.now();                   // problem 2

        if (today.getDayOfMonth() == 1) {
            return gateway.send(user, "Your monthly payment is due.");
        }
        return false;
    }
}
