package com.example.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Starts the SAME app twice in one JVM - two instances behind a load balancer, as in production.
class DigestJobTest {

    @BeforeEach
    void reset() {
        EmailProvider.DIGESTS_SENT.set(0);
        JobClaims.reset();
    }

    @Test
    void scheduledJob_runsOnEveryInstance() throws InterruptedException {
        try (ConfigurableApplicationContext a = start("digest-unguarded");
             ConfigurableApplicationContext b = start("digest-unguarded")) {
            Thread.sleep(500);
            assertThat(EmailProvider.DIGESTS_SENT.get()).isEqualTo(2);   // every customer got two digests
        }
    }

    @Test
    void claimingTheRun_letsOnlyOneInstanceSend() throws InterruptedException {
        try (ConfigurableApplicationContext a = start("digest-claimed");
             ConfigurableApplicationContext b = start("digest-claimed")) {
            Thread.sleep(500);
            assertThat(EmailProvider.DIGESTS_SENT.get()).isEqualTo(1);
        }
    }

    private static ConfigurableApplicationContext start(String profile) {
        return new SpringApplicationBuilder(AsyncSchedulingApplication.class).profiles(profile).run();
    }
}
