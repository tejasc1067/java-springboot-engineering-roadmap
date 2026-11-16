# Spring AMQP

Topic 10 used RabbitMQ through a template and raw channels. A real service consumes with `@RabbitListener`, declares its queues as beans, sends JSON, and has to decide what happens when processing a message fails. That last decision is where most RabbitMQ bugs live: Spring's default turns one bad message into an endless loop. This topic builds a small shipping consumer and walks through converters, acknowledgements, dead-lettering, retries, and publisher confirms, each as a test against a real broker.

---

## The problem this solves

shipping-service consumes `OrderPlaced` events. Most are fine. One arrives with a negative amount (a bug in order-service), and the listener throws. What should happen to that message?

- Dropping it loses an order.
- Retrying immediately fails the same way every time.
- And while the broker keeps handing it back, it competes with every healthy message on the queue.

You want: try a few times in case the failure is temporary, then move the message somewhere a person can look at it, and carry on with the rest of the queue.

## How it works

### Declaring the topology as beans

```java
@Bean
TopicExchange ordersExchange() {
    return new TopicExchange("orders");
}

@Bean
Queue shippingQueue() {
    return QueueBuilder.durable("shipping.orders").deadLetterExchange("orders.dlx").build();
}

@Bean
Binding shippingBinding() {
    return BindingBuilder.bind(shippingQueue()).to(ordersExchange()).with("order.placed.#");
}
```

Spring Boot's `RabbitAdmin` declares every `Exchange`, `Queue`, and `Binding` bean on the broker when the application first connects, and again after a reconnect. Declaring is idempotent: if the queue already exists with the same settings, nothing happens. If it exists with *different* settings (say, you add a dead-letter exchange to an existing queue), the declaration fails with `PRECONDITION_FAILED`, and you have to delete the queue or use a new name. That's worth knowing before you change a production queue's arguments.

### The listener

```java
@RabbitListener(queues = "shipping.orders")
public void onOrderPlaced(OrderPlaced order) {
    if (order.amountCents() < 0) {
        throw new IllegalStateException("negative amount on order " + order.orderId());
    }
    shipped.add(order);
}
```

Spring converts the message body into the parameter type, calls the method, and acknowledges the message when it returns normally. That's the default **AUTO** acknowledge mode: return means ack, throw means reject.

### Message converters: use JSON

Without a converter bean, `RabbitTemplate` uses `SimpleMessageConverter`, which handles `String`, `byte[]`, and Java serialization. `JavaSerializationConverterTest` shows both halves of why you don't want that:

- `aPlainRecord_cannotBeSentAtAll`: a record that doesn't implement `Serializable` fails on send: `SimpleMessageConverter only supports String, byte[] and Serializable payloads`.
- `aSerializableRecord_isSent_butTheReceiverRefusesToDeserializeIt`: make it `Serializable` and the receiving side throws `Attempt to deserialize unauthorized class`. Spring AMQP only deserializes classes on an allow-list, because deserializing Java objects from untrusted bytes is a well-known remote-code-execution risk.

Declare one bean and Spring Boot plugs it into the template and every listener:

```java
@Bean
Jackson2JsonMessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

`JsonConverterTest` reads the raw message: content type `application/json`, body `{"orderId":"o-1","amountCents":1999}`. It also carries a `__TypeId__` header with the sender's class name, the same coupling as Spring Kafka's header in [`09-schemas-and-serialization.md`](09-schemas-and-serialization.md). For a `@RabbitListener` with a typed parameter, Spring uses the parameter's type rather than the header, so the consumer's own class wins. The rules from topic 09 for changing events apply here unchanged.

### Broken: the poison message loop

The default: a listener that throws gets its message **rejected with requeue**. The broker puts it back at the head of the queue and delivers it again, at once. `PoisonMessageTest.failingListener_withDefaultSettings_redeliversTheSameMessageEndlessly`:

```java
rabbit.convertAndSend("orders", "order.placed.eu", new OrderPlaced("bad-1", -500));

await().until(() -> shipping.failedAttempts() >= 100);   // reached in well under a second
```

A hundred failures in a fraction of a second, each logging a stack trace, burning CPU, and never stopping on a classic queue. A message that can never succeed is called a **poison message**.

RabbitMQ 4 limits the damage on **quorum queues** (the replicated queue type recommended for production): they have a default **delivery limit of 20**. `quorumQueue_givesUpAfterItsDeliveryLimit_andDeadLettersTheMessage` counts 21 deliveries (the first plus 20 redeliveries), after which the broker dead-letters the message, or drops it if the queue has no dead-letter exchange. That's a safety net, not a design: 21 instant attempts aren't a retry strategy.

### Fixed: reject without requeue, to a dead-letter exchange

Two pieces:

1. The queue has a **dead-letter exchange** (`x-dead-letter-exchange`, set by `QueueBuilder.deadLetterExchange(...)` above). A message the queue rejects without requeue, or that expires, is republished there instead of being dropped. A dead-letter queue (DLQ) bound to that exchange holds it.
2. The listener rejects **without requeue**. Either set it for every listener:

   ```yaml
   spring.rabbitmq.listener.simple.default-requeue-rejected: false
   ```

   or throw `AmqpRejectAndDontRequeueException` for failures you know are permanent (validation errors, unparseable data) and let other exceptions requeue.

`DeadLetterTest.failedMessage_isTriedOnce_andLandsInTheDeadLetterQueue` sends the bad order and then a good one:

- the bad order is tried **once** and arrives in `shipping.orders.dlq`;
- its `x-death` header says `reason = rejected`, `queue = shipping.orders`, so you know where it came from and why;
- the good order behind it is shipped normally.

Someone must watch the DLQ: alert on its depth, look at the messages, fix the cause, then move them back to the main queue (the management UI's "move messages" or a small script). A DLQ nobody reads is just a slower way to lose messages.

### Retry first, then dead-letter

Some failures are temporary: a database blip, a timeout from another service. For those, retry a few times before giving up:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1s
          multiplier: 2
```

`RetryThenDeadLetterTest`: the listener is called **3 times**, then the message goes to the DLQ. The retries happen **inside the listener's thread**, with backoff, before the broker hears anything. The broker delivered the message once and saw one rejection (`x-death` count is 1). When retries run out, Spring Boot's default recoverer, `RejectAndDontRequeueRecoverer`, rejects without requeue, so the dead-letter exchange takes over. It logs `Retries exhausted` with the message.

Two consequences:

- While a message is being retried, that consumer thread is busy. Long backoffs on many messages stall the queue. Keep in-listener retry short (seconds), and handle longer delays another way (a delay queue with a TTL that dead-letters back to the main queue).
- A retried message may have partly succeeded the first time. The listener must be idempotent ([`08-idempotent-consumers.md`](08-idempotent-consumers.md)).

### Publisher confirms and returns: did the broker get it?

`convertAndSend` returns as soon as the bytes are written to the socket. The broker might never get them, might fail to store them, or might route them nowhere. With **publisher confirms** the broker answers each message with an ack or a nack, and with **returns** it sends back messages that matched no queue:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

```java
CorrelationData correlation = new CorrelationData("order-1");
rabbit.convertAndSend("orders", "order.placed.eu", order, correlation);

CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
```

`PublisherConfirmsTest` shows the three outcomes:

| Situation | `confirm.isAck()` | `correlation.getReturned()` |
|---|---|---|
| routed to `shipping.orders` | `true` | `null` |
| routing key `invoice.created`, no matching binding | **`true`** | returned, `NO_ROUTE` |
| exchange `ordres` (typo) doesn't exist | `false`, reason contains `NOT_FOUND` | `null` |

The middle row is the trap from topic 10: an ack means the broker took responsibility for the message, and routing it to zero queues fulfils that. Check both the confirm and the return. A producer that must not lose messages waits for the ack (or handles a nack or timeout by retrying), which is also what the outbox relay in module 13 topic 19 does before marking a row as sent.

---

## When to use it / when not to

- **`@RabbitListener` with AUTO ack** suits almost every consumer. Use `MANUAL` acknowledgement only when you hand the message to other threads and must ack later.
- **Dead-lettering:** always, on every queue whose messages matter. It costs one exchange and one queue.
- **In-listener retry:** for short, temporary failures. Not for "the other service is down for an hour".
- **Publisher confirms:** for every message you can't afford to lose. They add a round trip per message (or per batch), so for high volume confirm asynchronously, as `CorrelationData` does, instead of waiting on each send.

---

## Common pitfalls

- **Leaving `default-requeue-rejected` at its default.** One bad message loops forever on a classic queue. Reject without requeue and dead-letter.
- **A dead-letter queue nobody monitors.** Messages pile up unnoticed. Alert on its depth.
- **Java serialization for messages.** Requires `Serializable`, ties consumers to the producer's classes, and is refused on receipt by default for security. Use `Jackson2JsonMessageConverter`.
- **Trusting `convertAndSend` returning normally.** It doesn't mean the broker has the message. Enable confirms, and check returns as well as acks.
- **Changing a declared queue's arguments.** Redeclaring with different arguments fails with `PRECONDITION_FAILED`. Plan a new queue and migrate.

---

## Code examples

`CODE_EXAMPLES/11-spring-amqp/spring-amqp-demo/`. Run with `mvn -q test` (Docker must be running). Read in this order:

1. `src/main/java/com/example/amqp/AmqpConfig.java` — exchange, queue with a dead-letter exchange, bindings, the JSON converter.
2. `src/main/java/com/example/amqp/ShippingListener.java` — the `@RabbitListener`, which fails on negative amounts.
3. `src/main/resources/application.yml` — publisher confirms, returns, mandatory.
4. `src/test/java/com/example/amqp/RabbitTestBase.java` — the shared container; a fresh context per test.
5. `src/test/java/com/example/amqp/JavaSerializationConverterTest.java` and `JsonConverterTest.java` — the default converter's two failures, then JSON.
6. `src/test/java/com/example/amqp/PoisonMessageTest.java` — the endless redelivery loop, and the quorum queue's delivery limit.
7. `src/test/java/com/example/amqp/DeadLetterTest.java` — one attempt, then the DLQ, and the next message unaffected.
8. `src/test/java/com/example/amqp/RetryThenDeadLetterTest.java` — three attempts in the listener, then the DLQ.
9. `src/test/java/com/example/amqp/PublisherConfirmsTest.java` — ack, ack plus return, nack.

---

## Try this yourself

1. In `ShippingListener`, throw `AmqpRejectAndDontRequeueException` for negative amounts instead of `IllegalStateException`, and run `PoisonMessageTest.failingListener_withDefaultSettings_redeliversTheSameMessageEndlessly` again. What changes, and why don't you need `default-requeue-rejected=false` any more?
2. Add `x-message-ttl` of 5 seconds to a new queue with no consumer, bound to `orders` with the same dead-letter exchange. Publish to it and check the DLQ after the TTL. What does the `x-death` reason say?

## Self-check

1. A listener throws on one message, and the logs fill with the same stack trace hundreds of times a second. Explain what Spring and RabbitMQ are doing, and the two settings that fix it.
2. What's the difference between in-listener retry and requeueing? Where is the message while each is happening, and what does the broker see?
3. A send's confirm comes back with `ack = true`, but no consumer ever sees the message. How is that possible, and how would the publisher have found out?
