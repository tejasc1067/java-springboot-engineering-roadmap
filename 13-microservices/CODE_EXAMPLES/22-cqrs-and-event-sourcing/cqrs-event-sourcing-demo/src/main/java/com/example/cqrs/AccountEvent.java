package com.example.cqrs;

// Events are facts in the past tense. Once appended they are never changed or deleted.
public sealed interface AccountEvent {
    record Opened(String owner) implements AccountEvent { }
    record Deposited(long cents) implements AccountEvent { }
    record Withdrawn(long cents) implements AccountEvent { }
}
