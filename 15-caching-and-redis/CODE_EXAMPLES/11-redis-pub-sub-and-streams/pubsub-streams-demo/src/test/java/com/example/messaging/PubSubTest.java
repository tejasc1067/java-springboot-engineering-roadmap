package com.example.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class PubSubTest extends RedisTestBase {

    private final List<String> received = new CopyOnWriteArrayList<>();
    private RedisMessageListenerContainer subscriber;

    @AfterEach
    void stopSubscriber() throws Exception {
        if (subscriber != null) subscriber.destroy();
    }

    @Test
    void runningSubscriber_receivesWhatIsPublished() {
        startSubscriber("orders");
        waitUntilSubscribed("orders");

        redis.convertAndSend("orders", "order-1 placed");

        await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("order-1 placed"));
    }

    @Test
    void publishWithNoSubscriber_isDroppedAtOnce_andNeverDeliveredLater() {
        Long receivers = redis.convertAndSend("orders", "order-1 placed");   // PUBLISH returns how many got it
        assertThat(receivers).isZero();                                       // nobody: Redis discards it

        startSubscriber("orders");                       // the subscriber "comes back up"
        waitUntilSubscribed("orders");
        redis.convertAndSend("orders", "order-2 placed");

        await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("order-2 placed"));
        // order-2 arrived after order-1 was sent, and order-1 still isn't here: it's gone for good.
        assertThat(received).containsExactly("order-2 placed");
    }

    @Test
    void everySubscriberGetsItsOwnCopy_messagesAreBroadcastNotShared() throws Exception {
        List<String> secondCopy = new CopyOnWriteArrayList<>();
        startSubscriber("orders");
        RedisMessageListenerContainer second = new RedisMessageListenerContainer();
        second.setConnectionFactory(connectionFactory);
        second.addMessageListener((message, pattern) -> secondCopy.add(new String(message.getBody())), new ChannelTopic("orders"));
        second.afterPropertiesSet();
        second.start();
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> redis.convertAndSend("orders", "probe") == 2);
            received.clear();
            secondCopy.clear();

            redis.convertAndSend("orders", "order-1 placed");

            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("order-1 placed")
                    && secondCopy.contains("order-1 placed"));
        } finally {
            second.destroy();
        }
    }

    private void startSubscriber(String channel) {
        subscriber = new RedisMessageListenerContainer();
        subscriber.setConnectionFactory(connectionFactory);
        subscriber.addMessageListener((message, pattern) -> received.add(new String(message.getBody())), new ChannelTopic(channel));
        subscriber.afterPropertiesSet();
        subscriber.start();
    }

    /**
     * Subscribing happens in the background. PUBLISH reports how many subscribers received a message,
     * so wait until the channel has one ("probe" messages sent before that reach nobody, by definition).
     */
    private void waitUntilSubscribed(String channel) {
        await().atMost(Duration.ofSeconds(5)).until(() -> redis.convertAndSend(channel, "probe") > 0);
        await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("probe"));
        received.clear();
    }
}
