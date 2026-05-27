package com.example;

/**
 * The seam. Code depends on this interface, not on a concrete way of sending.
 * Production wires in a real implementation; a test wires in a fake.
 */
public interface MessageGateway {
    boolean send(String to, String message);
}
