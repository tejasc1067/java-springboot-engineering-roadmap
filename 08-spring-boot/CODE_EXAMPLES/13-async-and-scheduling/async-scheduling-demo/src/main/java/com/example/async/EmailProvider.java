package com.example.async;

import java.util.concurrent.atomic.AtomicInteger;

// Stands in for an external mail API. Static, because it's shared by every app instance in DigestJobTest.
public final class EmailProvider {
    public static final AtomicInteger DIGESTS_SENT = new AtomicInteger();
    private EmailProvider() { }
}
