package com.example;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestableRefactoredTest {

    /**
     * A hand-written fake gateway: instead of sending, it records what it was asked to send.
     * This is a "test double". Topic 08 shows how Mockito generates one of these for you.
     */
    static class RecordingGateway implements MessageGateway {
        final List<String> sent = new ArrayList<>();

        @Override
        public boolean send(String to, String message) {
            sent.add(to + " | " + message);
            return true;
        }
    }

    /** A Clock frozen at midnight UTC on the given date, so "today" is whatever we say. */
    private Clock clockOn(String isoDate) {
        return Clock.fixed(Instant.parse(isoDate + "T00:00:00Z"), ZoneId.of("UTC"));
    }

    @Test
    void sendsReminderOnTheFirstOfTheMonth() {
        RecordingGateway gateway = new RecordingGateway();
        TestableRefactored service = new TestableRefactored(gateway, clockOn("2026-03-01"));

        boolean sent = service.sendDueReminder("ada@example.com");

        assertThat(sent).isTrue();
        assertThat(gateway.sent)
                .containsExactly("ada@example.com | Your monthly payment is due.");
    }

    @Test
    void doesNotSendOnOtherDays() {
        RecordingGateway gateway = new RecordingGateway();
        TestableRefactored service = new TestableRefactored(gateway, clockOn("2026-03-15"));

        boolean sent = service.sendDueReminder("ada@example.com");

        assertThat(sent).isFalse();
        assertThat(gateway.sent).isEmpty();   // nothing was sent
    }
}
