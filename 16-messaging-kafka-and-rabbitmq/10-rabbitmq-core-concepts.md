# RabbitMQ Core Concepts

RabbitMQ is a message broker built around **queues**: a producer hands it a message, RabbitMQ puts the message in one or more queues, and a consumer takes it off, processes it, and acknowledges it, at which point it's gone. That's a different model from Kafka's log (topic 01), and its vocabulary is different too: you never publish to a queue directly, you publish to an **exchange**, and **bindings** decide where the message goes. This topic is that routing model, the way a message silently disappears when no binding matches, and how RabbitMQ shares work between several consumers.

---

## The problem this solves

order-service publishes "order placed". Shipping wants every order. Billing wants every order. A fraud checker only wants orders from new customers in the EU. An email service wants "order placed" and also "order shipped" and "payment failed".

With plain queues, order-service would have to know every consumer and write a copy into each one's queue, and change its code every time a team wants a new copy. RabbitMQ moves that decision into the broker: order-service publishes once, with a **routing key** describing the message, and each consumer team declares which messages its queue wants.

## How it works

### Producer → exchange → binding → queue → consumer

```text
                         binding "eu"
producer --[key "eu"]--> [exchange] ------------> [queue orders.eu] --> consumer
                             |      binding "us"
                             +------------------> [queue orders.us] --> consumer
```

- **Exchange:** receives every published message. It stores nothing. Its only job is to decide which queues get a copy.
- **Queue:** stores messages until a consumer takes them. This is where messages wait.
- **Binding:** a rule connecting an exchange to a queue, usually with a **binding key** or pattern.
- **Routing key:** a short string the producer attaches to each message, such as `order.created.eu`.

The producer only knows the exchange name and the routing key. Adding a consumer means declaring a new queue and binding; the producer doesn't change.

### The four exchange types

`ExchangeTypesTest` declares each kind against a real broker and counts what lands in each queue. (The demo's `publish` helper waits for the broker to confirm each message before counting, so the counts are final.)

**Direct:** the routing key must equal the binding key exactly (`directExchange_routingKeyMustMatchTheBindingKeyExactly`):

```java
admin.declareBinding(BindingBuilder.bind(queue("orders.eu")).to(orders).with("eu"));
admin.declareBinding(BindingBuilder.bind(queue("orders.us")).to(orders).with("us"));

publish("orders.direct", "eu", "order 1");   // orders.eu: 1, orders.us: 0
```

**Topic:** the binding key is a pattern over dot-separated words. `*` matches exactly one word; `#` matches zero or more (`topicExchange_starMatchesOneWord_hashMatchesZeroOrMore`):

| Routing key | `order.*` | `order.#` | `*.*.eu` |
|---|---|---|---|
| `order.created` | yes | yes | no |
| `order.created.eu` | no | yes | yes |
| `order` | no | yes (zero words) | no |
| `payment.failed.eu` | no | no | yes |

Topic exchanges are the most common choice for events, because new consumers can subscribe to slices you didn't plan for.

**Fanout:** every bound queue gets a copy and the routing key is ignored (`fanoutExchange_everyBoundQueueGetsACopy_routingKeyIgnored`). Shipping and billing each get their own copy of every order.

**Headers:** routes on message header values instead of the routing key. It's rarely used; a topic exchange usually does the job more simply.

**The default exchange** has the empty name `""`. Every queue is automatically bound to it with its own name as the key, so publishing to `""` with routing key `emails` puts the message straight into the queue `emails` (`defaultExchange_routesToTheQueueNamedByTheRoutingKey`). That's why "publish to a queue" seems to work: you're publishing to the default exchange.

### Broken: a message no binding matches is dropped

order-service starts selling in Asia and publishes with routing key `asia`. Nobody has bound a queue for `asia` yet. `UnroutableTest.noMatchingBinding_theMessageIsDropped_andThePublishStillSucceeds`:

```java
publish("orders.routed", "asia", "order 7");   // the broker confirms it: no exception

depth("orders.routed.eu");                      // 0
```

The publish succeeded and the broker even confirmed it, because "confirmed" means "the broker took responsibility", and routing to zero queues counts as done. The message is gone, with no error and no log line. A typo in a routing key or a missing binding loses every message the same way.

### Fixed: get it back, or catch it

**The mandatory flag** asks the broker to return unroutable messages to the publisher instead of dropping them (`mandatoryFlag_theBrokerReturnsTheMessageToThePublisher`):

```java
RabbitTemplate mandatory = new RabbitTemplate(connectionFactory);   // with spring.rabbitmq.publisher-returns: true
mandatory.setMandatory(true);
mandatory.setReturnsCallback(returned -> log.error("unroutable: {}", returned));   // replyText "NO_ROUTE"
```

In a Spring Boot app, `spring.rabbitmq.template.mandatory: true` does the same for the auto-configured template. Your code decides what to do: log loudly, alert, store it for later.

**An alternate exchange** catches unroutable messages inside the broker, so nothing depends on the publisher's code (`alternateExchange_catchesWhatNoBindingMatched`):

```java
ExchangeBuilder.directExchange("orders.with-ae").alternate("orders.unrouted").build();
// a fanout exchange "orders.unrouted" bound to a queue someone monitors
```

### Durable queues and persistent messages

RabbitMQ keeps messages in memory and, optionally, on disk. Surviving a broker restart needs **both**:

- a **durable** queue (the queue definition survives a restart), and
- **persistent** messages (delivery mode 2: each message is written to disk).

A durable queue holding non-persistent messages comes back empty after a restart. A non-durable queue disappears entirely, messages included.

The raw RabbitMQ Java client defaults to neither. Spring AMQP defaults to both: `new Queue("orders")` is durable, and `RabbitTemplate` sends persistent messages (`springDefaults_durableQueue_andPersistentMessages`). The demo's own test queues are deliberately non-durable, so they clean up after themselves.

Persistent messages are only safe once the broker has confirmed them (publisher confirms, topic 11). A message the broker never confirmed may never have reached the disk.

### Competing consumers and prefetch

Several consumers on **one** queue share its messages: each message goes to exactly one of them. That's how you scale processing: add consumers. (Several consumers that each need *every* message use separate queues, as with fanout above.)

Each consumer acknowledges a message when it has finished with it. Until then the message is **unacked**: still owned by the queue, reserved for that consumer. If the consumer's connection closes, its unacked messages go back on the queue for someone else.

**Prefetch** limits how many unacked messages the broker pushes to one consumer at a time. `PrefetchTest` has two consumers on one queue. One is healthy and acks immediately. The other is stuck: it receives messages and never finishes them, like a consumer blocked on a hung HTTP call.

**Broken: no prefetch limit** (`unlimitedPrefetch_roundRobinHandsHalfTheMessagesToTheStuckConsumer`). The broker hands out the 10 messages in turn, 5 each. The stuck consumer holds 5 that will wait until its connection dies, while the healthy one finishes its 5 and sits idle.

**Fixed: prefetch 1** (`prefetchOne_theStuckConsumerHoldsOneMessage_theHealthyOneDoesTheRest`). The stuck consumer may hold only one unacked message, so the broker sends everything else to the healthy one: 1 against 9. With slow and fast consumers rather than stuck ones, the same setting means each consumer gets work as fast as it finishes it.

A small prefetch costs throughput, because a consumer waits a round trip for each next message. Spring AMQP's listener default is 250, which suits fast, even work. Lower it when processing is slow or uneven (`spring.rabbitmq.listener.simple.prefetch`).

---

## When to use it / when not to

- **RabbitMQ fits** work queues (jobs that each need doing once, by any worker), routing messages by type or attribute to different consumers, and per-message acknowledgement and retry.
- **Kafka fits better** when consumers need to replay history, when you need many independent readers of a high-volume stream, or when ordering within a key matters across a large scale. Topic 12 compares them.

---

## Common pitfalls

- **Assuming a successful publish means a queue has the message.** Routing to zero queues succeeds and drops the message. Use the mandatory flag or an alternate exchange.
- **Durable queue, non-persistent messages (or the reverse).** One without the other loses messages on a broker restart.
- **Default prefetch with slow or uneven work.** One slow consumer hoards messages that idle consumers could process. Lower the prefetch.
- **Treating a queue like a topic.** Two services consuming from one queue split the messages between them; each sees only part. Give each service its own queue bound to the exchange.

---

## Code examples

`CODE_EXAMPLES/10-rabbitmq-core-concepts/rabbit-basics-demo/`. Run with `mvn -q test` (Docker must be running). Read in this order:

1. `src/test/java/com/example/rabbit/RabbitTestBase.java` — the RabbitMQ container, and a `publish` that waits for the broker's confirm.
2. `src/test/java/com/example/rabbit/ExchangeTypesTest.java` — default, direct, topic, and fanout exchanges; Spring's durable and persistent defaults.
3. `src/test/java/com/example/rabbit/UnroutableTest.java` — the silently dropped message, the mandatory flag, the alternate exchange.
4. `src/test/java/com/example/rabbit/PrefetchTest.java` — competing consumers and a stuck consumer, with and without a prefetch limit.

---

## Try this yourself

1. In `topicExchange_starMatchesOneWord_hashMatchesZeroOrMore`, add a queue bound with `#.eu` and publish `eu`. Does it match? Predict before you run it.
2. In `PrefetchTest`, try a prefetch of 3. How many messages does the stuck consumer end up holding, and why?

## Self-check

1. A producer publishes with routing key `order.shipped.eu`. Which of these bindings on a topic exchange match it: `order.*`, `order.#`, `*.shipped.*`, `#.eu`?
2. A new routing key has no binding yet. What happens to messages published with it, and what are two ways to stop losing them?
3. Two consumers share a queue and one of them hangs. Explain how prefetch decides how many messages are stuck behind it.
