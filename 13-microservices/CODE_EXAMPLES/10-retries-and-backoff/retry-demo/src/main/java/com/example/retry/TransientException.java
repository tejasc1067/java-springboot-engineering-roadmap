package com.example.retry;

/** A failure that might succeed if tried again — a dropped connection, a 503, a brief
    blip (fallacy 1). These are the ONLY failures worth retrying. */
public class TransientException extends RuntimeException {
    public TransientException(String message) {
        super(message);
    }
}
