package com.example.async;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Two jobs that each take 100 ms, both "every 200 ms" - but measured differently.
@Component
@Profile("rate-vs-delay")
public class RateVsDelayJobs {

    private final List<Long> rateStarts = new CopyOnWriteArrayList<>();
    private final List<Long> delayStarts = new CopyOnWriteArrayList<>();

    // fixedRate: start-to-start. Starts every 200 ms, however long each run takes.
    @Scheduled(fixedRate = 200)
    public void everyTwoHundredFromStart() throws InterruptedException {
        rateStarts.add(System.currentTimeMillis());
        Thread.sleep(100);
    }

    // fixedDelay: end-to-start. Waits 200 ms AFTER each run finishes, so starts are 300 ms apart.
    @Scheduled(fixedDelay = 200)
    public void twoHundredAfterFinishing() throws InterruptedException {
        delayStarts.add(System.currentTimeMillis());
        Thread.sleep(100);
    }

    public List<Long> rateStarts() { return rateStarts; }
    public List<Long> delayStarts() { return delayStarts; }
}
