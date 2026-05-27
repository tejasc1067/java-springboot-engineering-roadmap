package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * There is no clean unit test for HardToTestBroken. This documents why, in code.
 * The test is @Disabled so it is reported as skipped and never fails the build - it
 * exists to show the failure mode, not to pass.
 */
class HardToTestBrokenTest {

    @Test
    @Disabled("HardToTestBroken can't be unit-tested: we can't control 'today' and we can't "
            + "replace the gateway it builds with `new`. See the body and TestableRefactoredTest.")
    void thereIsNoCleanWayToTestThis() {
        HardToTestBroken broken = new HardToTestBroken();

        // We want to verify "on the 1st of the month, it sends a reminder".
        // But:
        //   - We can't make the class believe it's the 1st; it reads the real clock.
        //   - Even if it were the 1st, it builds its own SmtpMessageGateway and would
        //     attempt a real send (which throws here). We can't hand it a fake.
        // So the only "test" we could write would pass or fail based on today's date
        // and would try to touch a real mail server. That's not a unit test.
        broken.sendDueReminder("ada@example.com");
    }
}
