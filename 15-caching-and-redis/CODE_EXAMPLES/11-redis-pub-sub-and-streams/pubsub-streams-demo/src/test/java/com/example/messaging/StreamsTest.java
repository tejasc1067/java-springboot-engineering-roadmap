package com.example.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;

/**
 * A stream is an append-only log stored in Redis. A consumer group tracks, per group, what has been
 * delivered and what has been acknowledged, so messages wait for consumers instead of being dropped.
 */
class StreamsTest extends RedisTestBase {

    private static final String STREAM = "orders";
    private static final String GROUP = "email-service";

    private StreamOperations<String, Object, Object> streams;

    @BeforeEach
    void createGroup() {
        streams = redis.opsForStream();
        // XGROUP CREATE orders email-service 0 MKSTREAM: the group starts at the beginning of the stream
        streams.createGroup(STREAM, ReadOffset.from("0"), GROUP);
    }

    @Test
    void messagesAddedWhileNoConsumerRuns_areWaitingWhenItStarts() {
        streams.add(STREAM, Map.of("orderId", "1"));   // XADD orders * orderId 1
        streams.add(STREAM, Map.of("orderId", "2"));
        streams.add(STREAM, Map.of("orderId", "3"));   // ...the email service is down for all three

        List<MapRecord<String, Object, Object>> delivered = readNew("worker-1");

        assertThat(delivered).extracting(r -> r.getValue().get("orderId")).containsExactly("1", "2", "3");
    }

    @Test
    void eachMessageGoesToOneConsumerInTheGroup_soConsumersShareTheWork() {
        streams.add(STREAM, Map.of("orderId", "1"));
        streams.add(STREAM, Map.of("orderId", "2"));

        assertThat(readNew("worker-1", 1)).extracting(r -> r.getValue().get("orderId")).containsExactly("1");
        assertThat(readNew("worker-2", 1)).extracting(r -> r.getValue().get("orderId")).containsExactly("2");
        assertThat(readNew("worker-1")).isEmpty();   // nothing undelivered left for anyone in this group
    }

    @Test
    void consumerCrashesBeforeAck_messageStaysPending_andAnotherConsumerClaimsIt() {
        streams.add(STREAM, Map.of("orderId", "1"));

        RecordId id = readNew("worker-1").get(0).getId();
        // worker-1 crashes here: the email was never sent and XACK never ran.

        PendingMessages pending = streams.pending(STREAM, GROUP, org.springframework.data.domain.Range.unbounded(), 10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getConsumerName()).isEqualTo("worker-1");
        assertThat(pending.get(0).getTotalDeliveryCount()).isEqualTo(1);

        assertThat(readNew("worker-2")).isEmpty();   // ">" means "never delivered to anyone": it won't come back by itself

        // XCLAIM: worker-2 takes over messages idle for at least minIdleTime (in production: e.g. 1 minute,
        // so you don't steal from a consumer that is just slow; zero here to keep the test fast).
        List<MapRecord<String, Object, Object>> claimed = streams.claim(STREAM, GROUP, "worker-2", Duration.ZERO, id);
        assertThat(claimed).extracting(r -> r.getValue().get("orderId")).containsExactly("1");

        streams.acknowledge(STREAM, GROUP, id);      // XACK: the email was sent, done
        assertThat(streams.pending(STREAM, GROUP).getTotalPendingMessages()).isZero();
    }

    @Test
    void restartedConsumer_readsItsOwnPendingMessagesFirst() {
        streams.add(STREAM, Map.of("orderId", "1"));
        readNew("worker-1");                          // delivered, then worker-1 restarts before XACK

        // On start-up, read from id 0 instead of ">": that returns this consumer's own unacknowledged messages.
        List<MapRecord<String, Object, Object>> ownPending = streams.read(Consumer.from(GROUP, "worker-1"),
                StreamReadOptions.empty().count(10), StreamOffset.create(STREAM, ReadOffset.from("0")));

        assertThat(ownPending).extracting(r -> r.getValue().get("orderId")).containsExactly("1");
    }

    @Test
    void acknowledgedMessages_areStillInTheStream_untilTrimmed() {
        streams.add(STREAM, Map.of("orderId", "1"));
        streams.acknowledge(STREAM, GROUP, readNew("worker-1").get(0).getId());

        assertThat(streams.size(STREAM)).isEqualTo(1L);   // XACK clears the pending entry, not the message
        streams.trim(STREAM, 0);                            // XTRIM orders MAXLEN 0 (normally: keep the last N)
        assertThat(streams.size(STREAM)).isZero();
    }

    private List<MapRecord<String, Object, Object>> readNew(String consumer) {
        return readNew(consumer, 10);
    }

    // XREADGROUP GROUP email-service <consumer> COUNT n STREAMS orders >
    private List<MapRecord<String, Object, Object>> readNew(String consumer, int count) {
        return streams.read(Consumer.from(GROUP, consumer), StreamReadOptions.empty().count(count),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
    }
}
