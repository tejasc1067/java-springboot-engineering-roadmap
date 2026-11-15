package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// application.yml keeps Boot's default: spring.task.scheduling.pool.size = 1.
@SpringBootTest
@ActiveProfiles("starvation")
class SchedulerStarvationTest {

    @Autowired StarvationJobs jobs;

    @Test
    void oneSlowJob_blocksEveryOtherJob() throws InterruptedException {
        Thread.sleep(800);

        // The heartbeat should have run ~16 times in 800 ms. The slow job held the only thread.
        assertThat(jobs.heartbeats()).isLessThanOrEqualTo(2);
    }
}
