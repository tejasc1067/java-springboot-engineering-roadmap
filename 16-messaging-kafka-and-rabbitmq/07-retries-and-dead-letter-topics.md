# Retries and Dead-Letter Topics

Sooner or later a listener throws. Sometimes the cause goes away on its own (a database blip, a timeout) and a retry succeeds. Sometimes it never will: a bug, a missing field, bytes that aren't JSON. A message that fails every time is called a **poison message**, and how you handle it decides whether one bad record costs you one record or the whole partition. This topic covers what Spring Kafka does by default, the infinite-retry trap, dead-letter topics, the deserialization failure that bypasses all of it, and non-blocking retry topics.

---

## The problem this solves

Records in a partition are processed in order (topic 05). If the record at offset 41 fails, the consumer can't move on to 42 without deciding what happens to 41. It has three options: try 41 again, skip it, or put it aside somewhere. Retrying forever stops the partition. Skipping loses data. Putting it aside, in a **dead-letter topic**, keeps both the data and the partition moving.

## How it works

### What Spring Kafka does if you configure nothing

The listener container uses a `DefaultErrorHandler`. Its default back-off is `FixedBackOff(0, 9)`: **9 retries with no delay, so 10 attempts in total, then log the error and skip the record.**

```java
@KafkaListener(topics = "orders", groupId = "orders")
public void on(String order) {
    attempts.merge(order, 1, Integer::sum);
    if (order.startsWith("poison")) throw new IllegalStateException("cannot process " + order);
    processed.add(order);
}
```

`DefaultErrorHandlerTest.byDefault_aFailingRecordIsTriedTenTimes_thenLoggedAndSkipped`: `poison-1` is tried 10 times, then `order-2` is processed. All that's left of `poison-1` is one log line:

```text
ERROR o.s.kafka.listener.DefaultErrorHandler : Backoff FixedBackOff{interval=0, currentAttempts=10, maxAttempts=9} exhausted for orders-0@0
```

The partition keeps moving, but the order is gone unless someone reads the logs. And 10 attempts with no delay don't help with a database that's down for 30 seconds.

How the retry works matters: the error handler **seeks the consumer back** to the failed offset, and the next poll returns that record again. Retries happen on the consumer thread, so while one record is being retried, nothing else on that partition moves.

### Broken: retry forever

"We must never lose a message", so:

```java
new DefaultErrorHandler(new FixedBackOff(10, FixedBackOff.UNLIMITED_ATTEMPTS));
```

`InfiniteRetryTest.retryingForever_onePoisonRecordBlocksEveryRecordBehindIt`: `poison-1` is attempted again and again, and `order-2` and `order-3` are never processed. A poison message never succeeds, so the partition is blocked until someone deploys a fix or skips the offset by hand. Consumer lag grows on that partition, and every record behind the poison one waits: orders for customers who did nothing wrong.

Infinite retry is only right when the failure is guaranteed to be transient *and* waiting is better than moving on. That's rare.

### Fixed: retry a few times, then publish to a dead-letter topic

```java
DefaultErrorHandler handler = new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafka),   // after the last retry: publish to "<topic>-dlt"
        new FixedBackOff(100, 2));                  // 3 attempts, 100 ms apart
handler.addNotRetryableExceptions(IllegalArgumentException.class);
```

Declare it as a `CommonErrorHandler` bean and Spring Boot puts it on every listener container.

`DeadLetterTopicTest.afterTheRetries_theRecordGoesToTheDeadLetterTopic_withWhyInItsHeaders`: `poison-1` is tried 3 times, then published to `orders-dlt`, and `order-2` carries on. The dead-letter record has the original value and headers explaining what happened:

| Header | Example |
|---|---|
| `kafka_dlt-original-topic` | `orders` |
| `kafka_dlt-original-partition`, `kafka_dlt-original-offset` | where it came from |
| `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message` | `cannot process poison-1` |
| `kafka_dlt-exception-stacktrace` | the full stack trace |

Two details:

- **The default name is `<topic>-dlt`**, in the spring-kafka version Boot 3.5 uses (3.3). Older articles say `.DLT`; that was the default in earlier versions.
- **The record goes to the same partition number** in the dead-letter topic, so the dead-letter topic needs at least as many partitions as the original. Create it yourself with matching partitions.

### Some exceptions should never be retried

Retrying `IllegalArgumentException("order has no customer")` 3 times gives the same answer 3 times. `addNotRetryableExceptions(IllegalArgumentException.class)` sends it straight to the recoverer: `aNotRetryableException_goesStraightToTheDeadLetterTopic` shows 1 attempt.

Spring Kafka already treats these as not retryable: `DeserializationException`, `MessageConversionException`, `ConversionException`, `MethodArgumentResolutionException`, `NoSuchMethodException`, `ClassCastException`. Add your own validation exceptions to the list.

### Broken: a record that can't be deserialized

```java
@KafkaListener(topics = "shipments", groupId = "shipments", properties = {
        "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.json.value.default.type=com.example.retry.Shipment"})
public void on(Shipment shipment) { ... }
```

Someone sends `{not json` to `shipments`. Deserialization happens **inside `poll()`**, before Spring has a record to hand to your listener or to the error handler. There's nothing to retry, skip, or dead-letter. The error handler can only say so:

```text
Error deserializing VALUE for partition shipments-0 at offset 0
This error handler cannot process 'SerializationException's directly; please consider configuring an 'ErrorHandlingDeserializer'...
```

and the next poll fails on the same offset. `DeserializationErrorBrokenTest.badJson_withoutErrorHandlingDeserializer_failsEveryPoll_andBlocksThePartition`: the good shipment behind it is never delivered. In this demo the loop failed **about 1,600 times in under 0.2 seconds**, two ERROR lines with a stack trace each time: a full disk waiting to happen.

### Fixed: `ErrorHandlingDeserializer`

Wrap the real deserializer:

```java
"value.deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
"spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
```

or, in `application.yml`, for all listeners:

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

Now a failure doesn't throw inside `poll()`. The record comes back with the exception attached, the container hands it to the error handler, and `DeserializationException` is not retryable. `DeserializationErrorTest.badJson_withErrorHandlingDeserializer_goesToTheDeadLetterTopicOnTheFirstTry`: the bad bytes go to `shipments-dlt` unchanged, and the good shipment is processed.

Use `ErrorHandlingDeserializer` on every consumer whose input you don't fully control, which is every consumer.

### Non-blocking retries: `@RetryableTopic`

The retries above are **blocking**: while a record waits for its next attempt, the partition waits too. With a 30-second back-off, that's 30 seconds of nothing. `@RetryableTopic` moves the failed record to a **retry topic** instead, and the main topic carries on:

```java
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000))
@KafkaListener(topics = "payments", groupId = "payments")
public void on(String payment) { ... }

@DltHandler
public void onDeadLetter(String payment) { ... }
```

Spring creates the topics: with the defaults here, `payments-retry` (one topic, because the delay is the same for every retry) and `payments-dlt` (`RetryTopicTest.afterTheLastAttempt_theRecordGoesToTheDeadLetterTopic`). A consumer on the retry topic waits until the delay has passed, then tries again.

The cost is **ordering**. `aFailedRecordWaitsInARetryTopic_soTheRecordsBehindItOvertakeIt` sends `flaky-A` (fails once) then `payment-B`:

```java
assertThat(listener.processed()).containsExactly("payment-B", "flaky-A");
```

Sent A then B, processed B then A. If A and B were "order created" and "order cancelled" for the same order, the cancellation was handled before the creation. Use non-blocking retries only when records don't depend on each other's order (topic 05).

### What to do with the dead-letter topic

A dead-letter topic nobody reads is just a slower way of losing data.

1. **Alert on it.** A record arriving there is an incident. Monitor its size or consumer lag (module 17).
2. **Inspect it.** The headers say which exception, from where. Group by exception to find the bug.
3. **Fix the cause**, deploy.
4. **Replay**: read the dead-letter records and publish them back to their original topic. `afterTheFix_deadLettersAreReplayedToTheOriginalTopic`:

```java
listener.deployFix();
for (ConsumerRecord<String, String> dead : readDeadLetters(0)) {
    kafka.send(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC), dead.value());
}
```

Replay means the consumer sees those records again, possibly after newer ones for the same key, so the consumer must be idempotent (topic 08) and tolerate the reordering.

---

## When to use it / when not to

| Approach | Use when | Cost |
|---|---|---|
| Default (10 attempts, log and skip) | Never on purpose. Losing the record to a log line is rarely acceptable. | data loss |
| Blocking retries + dead-letter topic | The default choice. Short back-off, few attempts. | partition waits during retries |
| Infinite blocking retries | The failure is certainly transient and order matters more than progress | one poison message stops the partition |
| `@RetryableTopic` | Long back-offs (seconds to minutes) and no ordering requirement | ordering, extra topics |

## Common pitfalls

- **Relying on the default error handler.** After 10 instant attempts the record is gone, with one log line. Configure a `DeadLetterPublishingRecoverer`.
- **Retrying forever.** One poison message blocks the partition. Bound the attempts.
- **No `ErrorHandlingDeserializer`.** A single malformed record blocks the partition and floods the logs. Wrap every deserializer.
- **A dead-letter topic with fewer partitions than the original.** Publishing to partition 5 of a 1-partition topic fails. Match partition counts.
- **Retrying validation errors.** Add them to `addNotRetryableExceptions`.
- **`@RetryableTopic` on events that must stay in order.** Records overtake the failed one.

---

## Code examples

`CODE_EXAMPLES/07-retries-and-dead-letter-topics/retry-dlt-demo/`. Run with `mvn -q test` (no Docker). The tests print many ERROR lines on purpose. Read in this order:

1. `src/main/java/com/example/retry/OrderListener.java`: a listener with poison and invalid records.
2. `src/test/java/com/example/retry/DefaultErrorHandlerTest.java`: 10 attempts, then skipped.
3. `src/test/java/com/example/retry/InfiniteRetryTest.java`: one poison record blocks the partition.
4. `src/test/java/com/example/retry/DeadLetterTopicTest.java`: retries, then `orders-dlt`; not-retryable exceptions; replay after a fix.
5. `src/main/java/com/example/retry/ShipmentListenerBroken.java` and `src/test/java/com/example/retry/DeserializationErrorBrokenTest.java`: bad JSON without `ErrorHandlingDeserializer`.
6. `src/main/java/com/example/retry/ShipmentListener.java` and `src/test/java/com/example/retry/DeserializationErrorTest.java`: the same with it.
7. `src/main/java/com/example/retry/PaymentListener.java` and `src/test/java/com/example/retry/RetryTopicTest.java`: non-blocking retries and the ordering they give up.

---

## Try this yourself

1. In `DeadLetterTopicTest`, change the back-off to `new ExponentialBackOff(100, 2.0)` with `setMaxElapsedTime(1000)`. How many attempts does `poison-1` get now?
2. Change `PaymentListener`'s backoff to `@Backoff(delay = 1000, multiplier = 2)`. Which retry topics does Spring create now? (Hint: list them as `RetryTopicTest` does.)

## Self-check

1. With no error handler configured, what happens to a record whose listener always throws? How many attempts, and where does the record end up?
2. A malformed JSON record stopped a consumer and filled the disk with logs. Why didn't the dead-letter topic you configured catch it, and what fixes it?
3. When would you choose `@RetryableTopic` over blocking retries, and what do you give up?
