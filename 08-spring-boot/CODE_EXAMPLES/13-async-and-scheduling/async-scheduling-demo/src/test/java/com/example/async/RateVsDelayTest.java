package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.pool.size=2")
@ActiveProfiles("rate-vs-delay")
class RateVsDelayTest {

    @Autowired RateVsDelayJobs jobs;

    @Test
    void fixedDelay_waitsAfterTheRunEnds_fixedRate_doesNot() throws InterruptedException {
        Thread.sleep(1_500);

        // fixedDelay: 100 ms of work + 200 ms of waiting. Never less than 300 ms between starts.
        assertThat(gaps(jobs.delayStarts())).isNotEmpty().allMatch(gap -> gap >= 300);
        // fixedRate: start-to-start is 200 ms, so starts come closer together than that.
        assertThat(gaps(jobs.rateStarts())).anyMatch(gap -> gap < 300);
    }

    private static List<Long> gaps(List<Long> starts) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) gaps.add(starts.get(i) - starts.get(i - 1));
        return gaps;
    }
}
