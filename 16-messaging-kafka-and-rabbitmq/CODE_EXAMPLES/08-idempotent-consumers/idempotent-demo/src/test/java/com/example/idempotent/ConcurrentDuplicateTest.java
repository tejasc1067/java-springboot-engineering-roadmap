package com.example.idempotent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Two deliveries of the same event at the same moment: after a rebalance, the old owner of a partition
 * may still be finishing a record while the new owner starts on it again.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payments-naive", "payments-idempotent"})
class ConcurrentDuplicateTest {

    @Autowired
    AccountService accounts;

    @Test
    void checkThenInsert_withoutAConstraint_bothDeliveriesPassTheCheck() throws Exception {
        accounts.openAccount("acc-race");
        Payment payment = new Payment("evt-10", "acc-race", 100);
        CyclicBarrier bothChecked = new CyclicBarrier(2);

        List<Boolean> applied = runTwice(() -> accounts.creditCheckThenInsert(payment, () -> await(bothChecked)));

        assertThat(applied).containsExactly(true, true);
        assertThat(accounts.balanceOf("acc-race")).isEqualTo(200);
    }

    @Test
    void uniqueConstraint_onlyOneOfTwoConcurrentDeliveriesApplies() throws Exception {
        accounts.openAccount("acc-constraint");
        Payment payment = new Payment("evt-11", "acc-constraint", 100);

        List<Boolean> applied = runTwice(() -> accounts.creditOnce(payment));

        assertThat(applied).containsExactlyInAnyOrder(true, false);
        assertThat(accounts.balanceOf("acc-constraint")).isEqualTo(100);
    }

    private static List<Boolean> runTwice(java.util.concurrent.Callable<Boolean> delivery) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(delivery);
            Future<Boolean> second = pool.submit(delivery);
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
