package com.example.async;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("digest-claimed")
public class ClaimedDigestJob {

    @Scheduled(initialDelay = 0, fixedDelay = 3_600_000)
    public void sendDailyDigest() {
        if (!JobClaims.claim("daily-digest:" + LocalDate.now())) {
            return;                                     // another instance already ran today's digest
        }
        EmailProvider.DIGESTS_SENT.incrementAndGet();
    }
}
