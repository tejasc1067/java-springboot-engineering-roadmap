package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.pool.size=2")
@ActiveProfiles("starvation")
class SchedulerPoolTest {

    @Autowired StarvationJobs jobs;

    @Test
    void withTwoSchedulerThreads_theHeartbeatKeepsRunning() throws InterruptedException {
        Thread.sleep(800);

        assertThat(jobs.heartbeats()).isGreaterThanOrEqualTo(5);
    }
}
