package com.example.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class SynchronizedAcrossInstancesTest extends RedisContainerSupport {

    @Autowired StringRedisTemplate redis;

    StockService instanceA;
    StockService instanceB;

    @BeforeEach
    void oneItemInStock() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set("stock:lamp", "1");
        instanceA = new StockService(redis);
        instanceB = new StockService(redis);
    }

    @Test
    void synchronized_doesNotStopTheOtherInstance_soTheLastItemIsSoldTwice() throws Exception {
        CountDownLatch bothHaveRead = new CountDownLatch(2);
        Runnable waitForTheOther = () -> {
            bothHaveRead.countDown();
            await(bothHaveRead);
        };

        CompletableFuture<Boolean> saleA = CompletableFuture.supplyAsync(
                () -> instanceA.sellLocallySynchronized("lamp", waitForTheOther));
        CompletableFuture<Boolean> saleB = CompletableFuture.supplyAsync(
                () -> instanceB.sellLocallySynchronized("lamp", waitForTheOther));

        // Both read stock = 1 before either wrote: two customers paid for one lamp.
        assertThat(saleA.get()).isTrue();
        assertThat(saleB.get()).isTrue();
    }

    @Test
    void redisLock_isSeenByEveryInstance_soTheSecondSaleIsRefused() throws Exception {
        CountDownLatch bTried = new CountDownLatch(1);

        CompletableFuture<Boolean> saleA = CompletableFuture.supplyAsync(
                () -> instanceA.sellWithRedisLock("lamp", () -> await(bTried)));   // A holds the lock...
        while (!redis.hasKey("lock:stock:lamp")) {
            Thread.onSpinWait();
        }
        boolean saleB = instanceB.sellWithRedisLock("lamp", () -> { });       // ...while B tries
        bTried.countDown();

        assertThat(saleB).isFalse();
        assertThat(saleA.get()).isTrue();
        assertThat(redis.opsForValue().get("stock:lamp")).isEqualTo("0");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
