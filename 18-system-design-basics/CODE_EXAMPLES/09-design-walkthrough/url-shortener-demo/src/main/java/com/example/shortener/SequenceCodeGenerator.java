package com.example.shortener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter encoded in base62. Never collides, but the codes are predictable.
 * Each instance reserves a block of numbers at a time from a shared counter, so instances
 * don't need to coordinate on every request. The shared counter stands in for a database
 * sequence or a "ticket server".
 */
public class SequenceCodeGenerator implements CodeGenerator {

    private final AtomicLong sharedBlockCounter;
    private final int blockSize;
    private long next;
    private long blockEnd;

    public SequenceCodeGenerator(AtomicLong sharedBlockCounter, int blockSize) {
        this.sharedBlockCounter = sharedBlockCounter;
        this.blockSize = blockSize;
    }

    @Override
    public synchronized String generate(String longUrl) {
        if (next == blockEnd) {
            long block = sharedBlockCounter.getAndIncrement();
            next = block * blockSize;
            blockEnd = next + blockSize;
        }
        return Base62.encode(next++);
    }
}
