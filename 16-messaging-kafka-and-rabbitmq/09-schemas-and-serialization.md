# Schemas and Serialization

A message is bytes. The producer turns an object into bytes, and every consumer turns those bytes back into its own object. Those are different programs, owned by different teams, deployed on different days, and Kafka keeps the bytes for days or forever. So a message has to be readable by code that didn't exist when it was written, and by code older than the code that wrote it. This topic shows how a harmless-looking change to an event class breaks consumers, sometimes loudly and sometimes silently, and the rules that keep changes safe.

---

## The problem this solves

order-service publishes `OrderPlaced` to the `orders` topic. shipping-service, billing-service, and analytics-service consume it. Then order-service's team:

- moves `OrderPlaced` into an `events` package, or
- renames `amountCents` to `totalCents` because it reads better.

In a single application the compiler would find every place that broke. Here, nothing fails at build time. The consumers find out in production: either every message fails to deserialize, or worse, every message deserializes fine with the wrong data. And the topic still holds last week's messages in the old shape, so even consumers that upgrade have to read both.

## How it works

### What Spring Kafka's `JsonSerializer` actually sends

```java
kafka.send(new JsonSerializer<>(), new OrderPlaced("o-1", 1999));
```

`TypeHeaderTest.jsonSerializer_putsTheProducersClassNameInAHeader` reads the raw record:

```text
value:   {"orderId":"o-1","amountCents":1999}
headers: __TypeId__ = com.example.schema.orderservice.OrderPlaced
```

The JSON is fine. The header is the problem: it's the **producer's Java class name**. A `JsonDeserializer` uses that header by default to decide which class to create. That couples every consumer to the producer's package structure, a detail no consumer should know about.

### Broken: the consumer follows the header

**First failure, before anything changes.** The default `JsonDeserializer` only trusts classes in `java.util` and `java.lang`, so a header naming a class anywhere else is refused (`defaultDeserializer_refusesAClassOutsideItsTrustedPackages`):

```text
The class 'com.example.schema.orderservice.OrderPlaced' is not in the trusted packages
```

The common "fix" is `addTrustedPackages("*")`. Now the consumer creates whatever class the header names, provided that class is on its own classpath, which in another service it usually isn't.

**Then the producer moves its class.** `producerMovesItsClass_everyRecordFailsOnTheConsumer`: the header now says `com.example.schema.orderservice.events.OrderPlaced`, the consumer has no such class, and every record fails:

```text
ClassNotFoundException: com.example.schema.orderservice.events.OrderPlaced
```

A refactoring in one service took down another, and nobody changed the data.

### Fixed: the consumer decides the type

Each consumer reads into **its own** class and ignores the header:

```java
new JsonDeserializer<>(com.example.schema.shipping.OrderPlaced.class, false)   // false: don't use type headers
```

In `application.yml` that's:

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.value.default.type: com.example.shipping.OrderPlaced
        spring.json.use.type.headers: false
```

`fixedTargetType_ignoresTheHeader_andReadsIntoTheConsumersOwnClass` reads the record with the moved-class header without trouble. On the producer side, `spring.json.add.type.headers: false` stops sending the header at all.

When one topic carries several event types and the consumer does need to tell them apart, send a **logical name** instead of a class name. Each side maps it to its own class (`typeMapping_sendsALogicalName_thatEachSideMapsToItsOwnClass`):

```java
// producer: spring.json.type.mapping = orderPlaced:com.example.schema.orderservice.OrderPlaced
// consumer: spring.json.type.mapping = orderPlaced:com.example.schema.shipping.OrderPlaced
```

The header becomes `__TypeId__ = orderPlaced`, a name that's part of the contract, not of anyone's code.

### Field changes: some are safe, one is silent

`FieldEvolutionTest` keeps shipping-service on the old class, `OrderPlaced(String orderId, long amountCents)`, and changes what order-service sends.

**Adding a field is safe** (`addingAField_theOldConsumerIgnoresIt`). order-service adds `currency`; the old consumer reads `orderId` and `amountCents` and ignores the rest. That works because Spring Kafka's `JsonDeserializer` builds its own `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` turned off. Consumers that ignore what they don't understand are called **tolerant readers**, and they're what makes adding fields safe.

**A consumer with a plain `ObjectMapper` breaks on any new field** (`consumerWithAPlainObjectMapper_breaksOnAnyNewField`). `new ObjectMapper()` keeps Jackson's default, `FAIL_ON_UNKNOWN_PROPERTIES = true`:

```text
Unrecognized field "currency" ... not marked as ignorable
```

If you pass your own mapper, configure it to ignore unknown properties.

**Renaming a field is silently wrong** (`renamingAField_theOldConsumerSilentlyReadsZero`). order-service sends `{"orderId":"o-1","totalCents":1999}`. The old consumer finds no `amountCents`, so the `long` gets its default, `0`, and `totalCents` is ignored as an unknown field:

```java
assertThat(received).isEqualTo(new OrderPlaced("o-1", 0));   // no exception, no log line
```

Every order now ships with an amount of zero. The tolerant reader that made adding fields safe is what makes this silent. A rename is really "remove a field and add another", and removing a field a consumer relies on is never safe.

### The rules for changing an event

| Change | Safe? | Why |
|---|---|---|
| Add an optional field (with a default) | Yes | Old consumers ignore it; new consumers use the default for old messages |
| Add a required field (no default) | Only for old consumers | New consumers can't read old messages that lack it |
| Remove a field | Only if no consumer uses it | Consumers that read it get a default or fail |
| Rename a field | No | It's a remove plus an add |
| Change a field's type | No | `"1999"` isn't a `long` |
| Change what a field *means* (cents to euros) | No, and no tool will notice | Same name, same type, wrong data |

When you really need an incompatible change, publish a **new event type or a new topic** (`OrderPlacedV2` on `orders-v2`), run both for a while, and retire the old one once every consumer has moved. Module 13 topic 06 covers the same idea for HTTP contracts.

### Backward and forward compatibility

Two words you'll meet in every schema tool:

- **Backward compatible:** consumers using the **new** schema can read data written with the **old** one. Upgrade the **consumers first**, then the producer.
- **Forward compatible:** consumers still on the **old** schema can read data written with the **new** one. Upgrade the **producer first**.
- **Full:** both, so deploy order doesn't matter.

With Kafka, also remember that the topic holds old messages. A consumer that replays from the beginning needs backward compatibility with *every* version still in the topic, not just the previous one.

### Schemas and a schema registry

JSON has no schema of its own, so nothing checks a change before it ships. Teams that want a check use a schema format, most often **Avro** (or Protobuf), plus a **schema registry**: a service that stores every version of each topic's schema and refuses a new version that breaks the compatibility rule configured for that topic. The producer registers its schema before sending, and each message carries a small schema id instead of field names. Confluent Schema Registry is the common one.

The check the registry runs is in the Avro library itself. `AvroCompatibilityTest` runs it with no registry:

```java
SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema).getType();   // backward: reader new, writer old
SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema).getType();   // forward: reader old, writer new
```

| v2 change | Backward | Forward |
|---|---|---|
| add `currency` with `"default": "EUR"` | compatible | compatible |
| add `currency` with no default | **incompatible** | compatible |
| rename `amountCents` to `totalCents` | **incompatible** | **incompatible** |
| change `amountCents` from `long` to `string` | **incompatible** | **incompatible** |

`oldBytes_readWithTheNewSchema_getTheDefault` writes a v1 record and reads it with v2: `currency` comes back as `"EUR"`. It also shows why the registry is needed at all: Avro's binary encoding has no field names (the whole record is under 12 bytes), so the reader must know the exact schema the writer used.

---

## When to use it / when not to

- **JSON with a fixed target type and tolerant readers** is enough for most teams with a handful of services, as long as everyone follows the rules table. It's readable in any tool and easy to debug.
- **Avro or Protobuf with a registry** pays off when many teams produce and consume, when topics are kept for a long time, or when message size matters. The cost is one more service to run and a build step to generate classes from schemas.
- **Java serialization**: never, for messages. It ties consumers to the producer's exact classes and is a known security risk.

---

## Common pitfalls

- **Consumers following `__TypeId__`.** A package move in the producer breaks every consumer. Use a fixed target type (`spring.json.use.type.headers: false`) or logical type mappings.
- **`addTrustedPackages("*")` as the fix for "not in the trusted packages".** It hides the coupling instead of removing it, and lets the message pick which class gets created.
- **Renaming a field.** Old consumers read a default value with no error. Add the new field, keep the old one until every consumer has moved, then remove it.
- **A hand-made `ObjectMapper` in a consumer.** It fails on every new field. Disable `FAIL_ON_UNKNOWN_PROPERTIES`.
- **One bad record stopping a consumer.** A record that can't be deserialized fails inside `poll`. Wrap the deserializer in Spring Kafka's `ErrorHandlingDeserializer`, so the failure reaches the error handler and can go to a dead-letter topic ([`07-retries-and-dead-letter-topics.md`](07-retries-and-dead-letter-topics.md)).

---

## Code examples

`CODE_EXAMPLES/09-schemas-and-serialization/schema-demo/`. Run with `mvn -q test` (no Docker; Kafka runs embedded). Read in this order:

1. `src/main/java/com/example/schema/orderservice/OrderPlaced.java` and `shipping/OrderPlaced.java` — the same event, as each service defines it.
2. `src/test/java/com/example/schema/KafkaRoundTrip.java` — sends one record through the embedded broker and reads it back with the serializers under test.
3. `src/test/java/com/example/schema/TypeHeaderTest.java` — the `__TypeId__` header, trusted packages, a moved class, and the two fixes.
4. `src/main/java/com/example/schema/orderservice/OrderPlacedWithCurrency.java` and `OrderPlacedV2.java` — an added field and a renamed field.
5. `src/test/java/com/example/schema/FieldEvolutionTest.java` — the added field ignored, the renamed field read as 0, the strict mapper failing.
6. `src/test/resources/avro/*.avsc` — v1 of the schema and four candidate v2s.
7. `src/test/java/com/example/schema/AvroCompatibilityTest.java` — the registry's compatibility check, and old bytes read with a new schema.

---

## Try this yourself

1. Change shipping's `OrderPlaced.amountCents` from `long` to `Long` and re-run `renamingAField_theOldConsumerSilentlyReadsZero`. What does the consumer get now? Is that easier or harder to notice?
2. Write `v2-rename-with-alias.avsc`: rename `amountCents` to `totalCents` but add `"aliases": ["amountCents"]` to the field. Check it with `backward(...)` and `forward(...)`. Only one of them becomes compatible. Which one, and why does an alias in the *new* schema not help an *old* reader?

## Self-check

1. order-service moves its event class to another package and shipping-service starts failing on every message. What did the message carry that caused this, and what two configurations prevent it?
2. Why does renaming `amountCents` to `totalCents` produce no error in an old consumer? Give the safe sequence of releases for making that rename.
3. Your registry is set to backward compatibility. Which services do you deploy first, and why? Which change in the table would it reject?
