package com.example;

// A BUSINESS-RULE failure: the request is well-formed (a real book id, a positive count),
// but the domain can't satisfy it because stock is too low. Bean validation can't express
// this — it has no view of current stock. The advice maps it to 409 Conflict.
public class InsufficientCopiesException extends RuntimeException {
    public InsufficientCopiesException(Long bookId, int requested, int available) {
        super("book " + bookId + ": requested " + requested + " copies but only " + available + " available");
    }
}
