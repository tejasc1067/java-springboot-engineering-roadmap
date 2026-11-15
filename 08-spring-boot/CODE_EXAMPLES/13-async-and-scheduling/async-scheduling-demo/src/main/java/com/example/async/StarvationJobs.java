package com.example.async;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("starvation")
public class StarvationJobs {

    private final AtomicInteger heartbeats = new AtomicInteger();

    // A slow nightly-style job: holds its thread for 1 second.
    @Scheduled(initialDelay = 0, fixedDelay = 60_000)
    public void slowReport() throws InterruptedException {
        Thread.sleep(1_000);
    }

    // Should run every 50 ms. With one scheduler thread, it can't run while slowReport() holds that thread.
    @Scheduled(initialDelay = 0, fixedRate = 50)
    public void heartbeat() {
        heartbeats.incrementAndGet();
    }

    public int heartbeats() { return heartbeats.get(); }
}
