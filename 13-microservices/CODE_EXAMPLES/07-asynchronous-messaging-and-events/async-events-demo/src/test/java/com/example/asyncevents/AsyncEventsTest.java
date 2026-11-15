package com.example.asyncevents;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The behaviours that make asynchronous messaging different from a synchronous call.
 * Everything is deterministic (latches/counters), not timing-guessed.
 */
class AsyncEventsTest {

    @Test
    void eventsPublishedWhileTheConsumerIsDownAreBufferedThenProcessed() throws InterruptedException {
        MessageBroker<OrderPlacedEvent> broker = new MessageBroker<>();
        AtomicInteger processed = new AtomicInteger();

        // No consumer is running. Publish anyway — this is the key difference from a
        // synchronous call, which would fail if the other side were down.
        for (int i = 1; i <= 5; i++) {
            broker.publish(new OrderPlacedEvent((long) i, "SKU-BOOK", 1));
        }
        assertThat(broker.backlog()).isEqualTo(5);   // all accepted...
        assertThat(processed.get()).isZero();        // ...with nobody consuming yet

        // The consumer service comes online and catches up on the backlog.
        CountDownLatch done = new CountDownLatch(5);
        broker.startConsumer(e -> {
            processed.incrementAndGet();
            done.countDown();
        });

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(processed.get()).isEqualTo(5);
        assertThat(broker.backlog()).isZero();
        broker.stopConsumer();
    }

    @Test
    void publishingDoesNotBlockOnASlowConsumer() throws InterruptedException {
        MessageBroker<OrderPlacedEvent> broker = new MessageBroker<>();
        AtomicInteger processed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(3);

        // Consumer is slow: 200ms of "work" (e.g. calling an email provider) per event.
        broker.startConsumer(e -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            processed.incrementAndGet();
            done.countDown();
        });

        long start = System.nanoTime();
        for (int i = 1; i <= 3; i++) {
            broker.publish(new OrderPlacedEvent((long) i, "SKU-PEN", 1));
        }
        long publishMillis = (System.nanoTime() - start) / 1_000_000;

        // Publishing 3 events returned almost instantly — far faster than even ONE
        // 200ms unit of downstream work. A synchronous call would have blocked ~600ms.
        assertThat(publishMillis).isLessThan(200L);
        assertThat(processed.get()).isLessThan(3); // consumer still working; publisher already moved on

        assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(processed.get()).isEqualTo(3);
        broker.stopConsumer();
    }

    @Test
    void syncFailsWhenDownstreamIsDown_asyncBuffers() {
        // Synchronous: order-service calls notification-service directly; it's down,
        // so the call throws and the caller must deal with it right now.
        boolean downstreamDown = true;
        Callable<Void> synchronousNotify = () -> {
            if (downstreamDown) {
                throw new IllegalStateException("notification-service unavailable");
            }
            return null;
        };
        assertThatThrownBy(synchronousNotify::call).isInstanceOf(IllegalStateException.class);

        // Asynchronous: publishing the same intent as an event succeeds even though no
        // consumer is up; it will be delivered when notification-service returns.
        MessageBroker<OrderPlacedEvent> broker = new MessageBroker<>();
        broker.publish(new OrderPlacedEvent(1L, "SKU-BOOK", 1));
        assertThat(broker.backlog()).isEqualTo(1);
    }

    @Test
    void aPoisonMessageWithNoErrorHandlingKillsTheConsumer() throws InterruptedException {
        MessageBroker<OrderPlacedEvent> broker = new MessageBroker<>();
        AtomicInteger processed = new AtomicInteger();
        broker.publish(new OrderPlacedEvent(1L, "SKU-BOOK", 1));
        broker.publish(new OrderPlacedEvent(2L, "SKU-POISON", 1)); // a message the consumer can never handle
        broker.publish(new OrderPlacedEvent(3L, "SKU-PEN", 1));

        broker.startConsumer(e -> {
            if (e.sku().equals("SKU-POISON")) {
                throw new IllegalArgumentException("cannot parse " + e.sku());
            }
            processed.incrementAndGet();
        });
        waitUntilDead(broker);

        // One bad message, and a perfectly good one behind it is stuck forever.
        assertThat(processed.get()).isEqualTo(1);
        assertThat(broker.backlog()).isEqualTo(1);
    }

    @Test
    void retriesThenDeadLetterKeepsTheQueueMoving() throws InterruptedException {
        MessageBroker<OrderPlacedEvent> broker = new MessageBroker<>();
        AtomicInteger poisonAttempts = new AtomicInteger();
        CountDownLatch goodOnes = new CountDownLatch(2);
        broker.publish(new OrderPlacedEvent(1L, "SKU-BOOK", 1));
        broker.publish(new OrderPlacedEvent(2L, "SKU-POISON", 1));
        broker.publish(new OrderPlacedEvent(3L, "SKU-PEN", 1));

        broker.startConsumerWithDeadLetter(e -> {
            if (e.sku().equals("SKU-POISON")) {
                poisonAttempts.incrementAndGet();
                throw new IllegalArgumentException("cannot parse " + e.sku());
            }
            goodOnes.countDown();
        }, 3);

        assertThat(goodOnes.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(poisonAttempts.get()).isEqualTo(3);                      // bounded retries, not forever
        assertThat(broker.deadLetters()).extracting(OrderPlacedEvent::orderId)
                .containsExactly(2L);                                       // parked for a human to inspect
        assertThat(broker.consumerAlive()).isTrue();
        broker.stopConsumer();
    }

    private static void waitUntilDead(MessageBroker<?> broker) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (broker.consumerAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(broker.consumerAlive()).isFalse();
    }
}
