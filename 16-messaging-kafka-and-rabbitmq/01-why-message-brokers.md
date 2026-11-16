# Why Message Brokers

Module 13 topic 07 made the case for asynchronous messaging: publish an event, return, and let other services react when they're ready. Its demo used an in-memory queue as a stand-in broker. This topic explains what a real broker adds over that stand-in, and the one design choice that separates the two brokers in this module. **RabbitMQ is a queue**: a message is delivered to one consumer and then deleted. **Kafka is a log**: messages are kept, and every reader keeps its own bookmark. Almost every difference between them follows from that one choice.

The demo is plain Java with no broker, so you can see the two models before any configuration gets in the way.

---

## The problem this solves

Module 13's in-memory queue showed the idea, but it can't run in production:

- **It lives inside one JVM.** Another service, on another machine, can't read it. Nor can a second instance of the same service.
- **It dies with the process.** A deploy or a crash loses every message that hadn't been processed yet.
- **It has one reading pattern.** A message is taken by one consumer and gone. Module 13 topic 07's first exercise found the consequence: two services can't both get every event from one queue.

A **message broker** is a separate server (or cluster) that every service connects to over the network. It stores messages on disk, replicated across machines, so they survive crashes. It tracks who has received what. And it decides how messages are shared out among readers, which is where the two models differ.

## How it works

### The queue model (RabbitMQ, Amazon SQS, JMS brokers)

A queue is a to-do list. Producers add messages; consumers take them off. **Each message goes to exactly one consumer, and once that consumer acknowledges it, the broker deletes it.**

```java
public synchronized void publish(String message) {
    queue.addLast(message);
}

/** Takes the next message off the queue. Nobody else will ever see it. */
public synchronized Optional<String> receive() {
    return Optional.ofNullable(queue.pollFirst());
}
```

What that gives you (`QueueModelTest`):

- **`aConsumedMessage_isGone`**: after one `receive()`, the queue is empty.
- **`competingConsumers_eachMessageGoesToExactlyOne`**: two workers of the email service pull from one queue and split six orders 3 and 3, with no order emailed twice. To process faster, add workers. This is called **competing consumers**, and it's what queues are best at.

And what it costs:

- **`aSecondServiceOnTheSameQueue_stealsMessagesInsteadOfGettingItsOwnCopy`**: point the email service and the analytics service at the same queue, and each gets *half* the orders. A queue can't fan out. Each interested service needs its own queue with its own copy of every message; RabbitMQ does that copying with *exchanges* (topic 10).
- **`aServiceAddedLater_cannotSeeAnythingPublishedBeforeIt`**: next month a fraud-check service wants every order ever placed. The queue deleted them as they were consumed.

### The log model (Kafka)

A log is an append-only list that nobody deletes from when they read. Each message gets a position, its **offset** (0, 1, 2, ...). A reader doesn't take messages; it remembers **how far it has read**. Different readers remember different positions.

```java
public synchronized long publish(String message) {
    log.add(message);
    return log.size() - 1;                                   // the offset
}

public synchronized List<String> poll(String group, int max) {
    int from = committedOffsets.getOrDefault(group, 0);       // a new group starts at the beginning
    int to = Math.min(log.size(), from + max);
    committedOffsets.put(group, to);                          // the only thing reading changes
    return List.copyOf(log.subList(from, to));
}
```

The bookmark belongs to a **consumer group**: all instances of one service share one group, and so one position. `LogModelTest`:

- **`readingDoesNotRemove_messagesStayInTheLog`**: after the email group reads a message, it's still in the log. Only the email group's offset moved.
- **`everyConsumerGroup_getsEveryMessage_atItsOwnPace`**: the email and analytics groups both get all three orders. Analytics reads one at a time and falls behind; that doesn't affect email at all.
- **`aGroupAddedLater_replaysFromOffsetZero`**: fraud-check is deployed next month and reads the whole history.
- **`afterABugFix_aGroupCanRewindAndProcessEverythingAgain`**: analytics had a bug. Move its offset back to 0 and reprocess.

The cost: the log keeps growing, so Kafka deletes old messages by **retention** (by default, after 7 days), not by consumption. And because nothing is removed on read, "each message to exactly one worker" has to be built differently. Kafka does it with partitions (topic 02).

### Side by side

| | Queue (RabbitMQ) | Log (Kafka) |
|---|---|---|
| After a message is processed | deleted | kept until retention expires |
| Several services want every message | one queue per service, copies made by the broker | one topic; each service is its own consumer group |
| A new service wants the history | it's gone | read from offset 0 |
| Reprocess after a bug | only what's in the dead-letter queue | rewind the group's offset |
| What the broker tracks per reader | each message's delivery and acknowledgement | one offset per group per partition |
| Typical use | task queues, commands, work spread over workers, flexible routing | event streams, many independent consumers, replay, high volume |

Neither is better in general. Topic 12 compares them properly, together with Amazon SQS.

### What every broker adds over an in-memory queue

Whichever model, a broker gives you what module 13's stand-in couldn't:

- **Durability.** Messages are written to disk and copied to other machines. A crashed broker or consumer loses nothing that was acknowledged.
- **Decoupled lifetimes.** Producers and consumers are separate processes that can be deployed, restarted, and scaled independently. A consumer that's down for an hour catches up when it comes back.
- **Delivery tracking.** The broker knows what each consumer has acknowledged, so a consumer that crashes mid-message gets it again. That's at-least-once delivery, and it's why consumers must handle duplicates (topics 06 and 08).
- **Load levelling.** A burst of 10,000 messages waits in the broker while consumers work through it at their own speed, instead of 10,000 simultaneous calls hitting a service.

What it costs: another distributed system to run and monitor, network hops on every message, and all the semantics module 13 topic 07 listed (eventual consistency, duplicates, limited ordering).

---

## When to use it / when not to

Use a broker when module 13 topic 07's conditions hold (the work can happen later, or several services react to one event) **and** the messages must survive a crash or reach other processes. Choose the model by how messages are read:

- **One worker should do each job** (send this email, resize this image): a queue.
- **Several services each want every event, now or later** (order placed: email, analytics, fraud-check next month): a log.

Don't add a broker for work inside one service that can be lost on restart. A thread pool or `@Async` (module 08 topic 13) is enough. And don't add one when the caller needs the answer to continue: that's a synchronous call.

---

## Common pitfalls

- **Pointing two services at one queue to "share" events.** Each gets half of them. Give each service its own queue, or use a log.
- **Assuming a queue keeps history.** Once consumed, a message is gone. If you'll ever want to replay, that's a log, or a database table.
- **Assuming a log deletes what's been read.** It doesn't. Disk use is set by retention, not by how fast consumers are.
- **Thinking a broker makes delivery exactly-once.** Every broker redelivers after a consumer crash. Design consumers for duplicates (topic 08).

---

## Code examples

`CODE_EXAMPLES/01-why-message-brokers/broker-models-demo/`. Run with `mvn -q test` (no Docker, no broker). Read in this order:

1. `src/main/java/com/example/brokers/QueueBroker.java` — the queue model: receive removes.
2. `src/test/java/com/example/brokers/QueueModelTest.java` — competing consumers, no fan-out, no history.
3. `src/main/java/com/example/brokers/LogBroker.java` — the log model: offsets per consumer group.
4. `src/test/java/com/example/brokers/LogModelTest.java` — every group gets everything, a late group replays, rewinding after a bug.

---

## Try this yourself

1. Give `QueueBroker` fan-out: let it hold one queue per subscriber name and copy every published message into each. Then make the email and analytics test pass with both services receiving both orders. What happens to a subscriber registered *after* a message was published?
2. Add a `retentionCount` to `LogBroker` that drops the oldest message whenever the log grows past it. What should `poll` do for a group whose offset points at a message that has already been deleted? (Kafka's answer is the `auto.offset.reset` setting from topic 04.)

## Self-check

1. In a queue and in a log, what happens to a message after a consumer has processed it?
2. Three services must all react to every `OrderPlaced` event, and a fourth will be added next quarter and needs last month's orders. Which model fits, and why does the other one struggle?
3. Name three things a broker gives you that module 13 topic 07's in-memory queue couldn't.
