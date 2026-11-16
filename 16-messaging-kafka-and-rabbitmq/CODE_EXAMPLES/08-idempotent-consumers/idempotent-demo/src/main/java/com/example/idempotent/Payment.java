package com.example.idempotent;

/** Sent as "eventId:account:amount", e.g. "evt-1:acc-1:100". The eventId is created once, by the producer. */
public record Payment(String eventId, String account, long amount) {

    public static Payment parse(String value) {
        String[] parts = value.split(":");
        return new Payment(parts[0], parts[1], Long.parseLong(parts[2]));
    }

    public String value() {
        return eventId + ":" + account + ":" + amount;
    }
}
