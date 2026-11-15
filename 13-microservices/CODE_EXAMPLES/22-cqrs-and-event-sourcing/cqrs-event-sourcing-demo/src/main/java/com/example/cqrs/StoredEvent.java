package com.example.cqrs;

// position: place in the store's global log (projections read in this order).
// version:  place within one account's stream (concurrency checks use this).
public record StoredEvent(long position, String accountId, int version, AccountEvent event) { }
