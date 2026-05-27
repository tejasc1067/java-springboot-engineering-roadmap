package com.example;

/**
 * The "real" gateway. Pretend this opens an SMTP connection and actually emails someone.
 * It throws here so that if it ever runs during a test, the test fails loudly instead of
 * silently sending real mail - which is exactly the danger the broken design exposes us to.
 */
public class SmtpMessageGateway implements MessageGateway {

    @Override
    public boolean send(String to, String message) {
        throw new UnsupportedOperationException(
                "real SMTP send must never run inside a unit test");
    }
}
