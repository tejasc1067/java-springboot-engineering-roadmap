package com.example.async;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Real schedule would be cron = "0 0 7 * * *". It runs on startup here so the test doesn't wait for 7am.
@Component
@Profile("digest-unguarded")
public class UnguardedDigestJob {

    @Scheduled(initialDelay = 0, fixedDelay = 3_600_000)
    public void sendDailyDigest() {
        EmailProvider.DIGESTS_SENT.incrementAndGet();   // every running instance does this
    }
}
