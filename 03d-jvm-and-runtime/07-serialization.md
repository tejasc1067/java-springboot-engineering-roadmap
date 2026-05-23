# 07 — Serialization

Serialization is turning an in-memory object graph into a byte sequence — so you can store it, send it across the network, or persist it to a cache. Deserialization is the reverse. Java has built-in serialization (`implements Serializable` + `ObjectOutputStream`), but in modern backend systems you almost always use JSON instead, often via Jackson.

This topic covers both. The Java-native path is worth knowing because frameworks still touch it (sessions, RMI, some message brokers), and because it has a notorious security history. Modern alternatives — JSON, Protocol Buffers, Avro — solve different problems and have their own tradeoffs.

---

## The problem this solves

Two services talk over HTTP. Service A has a `User` object in memory; Service B needs that same `User`. They share no memory. The only thing that crosses the wire is bytes. Something has to:

1. On A: take the `User` and produce bytes.
2. On B: take those bytes and produce an equivalent `User`.

That's serialization + deserialization. Same problem when caching to Redis, writing to a file, publishing to Kafka, or holding a session in `HttpSession`.

---

## Java's native serialization

```java
class Customer implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private transient String password;          // excluded from serialization
}

// write
try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("cust.ser"))) {
    out.writeObject(new Customer("Alice", "s3cr3t"));
}

// read
try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("cust.ser"))) {
    Customer c = (Customer) in.readObject();
}
```

Mechanics:

- **`Serializable` is a marker interface.** No methods. Its presence tells `ObjectOutputStream` "this is allowed to be serialized."
- The whole **reachable object graph** is serialized. If `Customer` holds an `Address`, that gets written too. The framework follows references.
- **`transient` fields are skipped.** Used for passwords, computed caches, anything that shouldn't be in the byte stream.
- **`static` fields are not serialized.** They belong to the class, not the instance.
- **`serialVersionUID`** is a version stamp. Match must match between serializer and deserializer, or `InvalidClassException` is thrown.

---

## `serialVersionUID` — why it matters

If you write an object today and read it back tomorrow, the class might have changed. By default, the JVM auto-generates the SUID from a hash of the class's signature. Any change — adding a field, renaming a method — changes the hash, breaks deserialization.

```java
private static final long serialVersionUID = 1L;
```

Declaring it explicitly says "I take responsibility for version compatibility." If you add a field that defaults to `null`, the SUID stays the same and old payloads still deserialize (the new field is null). If you make a breaking change, bump the SUID.

Best practice: **always** declare `serialVersionUID` if a class is `Serializable`. The compiler warns when you don't.

---

## The security problem

`ObjectInputStream.readObject()` instantiates whatever class is named in the byte stream. If an attacker can feed it crafted bytes, they can:

- Invoke arbitrary `readObject` / `readResolve` methods on classpath classes.
- Trigger chains of method calls that end in `Runtime.exec(...)`.
- This isn't theoretical. Apache Commons Collections in 2015 — the famous "Java deserialization vulnerability" — was an industry-wide emergency.

**Practical rules:**

1. **Don't deserialize untrusted input.** Period.
2. If you must, use **`ObjectInputFilter`** (Java 9+) to whitelist allowed classes.
3. Prefer JSON / Protobuf for cross-process communication — they don't reconstruct arbitrary types from the bytes.

Java is gradually removing native serialization from new APIs. The JDK team has signaled it's a long-term goal to drop it. New code shouldn't pick it.

---

## JSON via Jackson (the modern default)

For HTTP APIs and most caches, Jackson is the standard. It uses reflection + annotations to map objects to JSON.

```java
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(customer);
Customer back = mapper.readValue(json, Customer.class);
```

Useful annotations:

```java
@JsonProperty("user_id") private long id;
@JsonIgnore private String password;
@JsonCreator public Customer(@JsonProperty("name") String name) { ... }
@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd") private LocalDate dob;
```

Jackson advantages over native Java serialization:

- **Human-readable.** Debuggable in logs, on the wire, with `curl`.
- **Cross-platform.** Any language can read JSON.
- **Safe.** Default Jackson doesn't reconstruct arbitrary types (unless you enable polymorphic typing, which is the same security hole).
- **Versioning is forgiving.** New fields in JSON are ignored; missing fields become defaults.

Disadvantages:

- **No object graph** — every shared reference becomes a duplicated subtree. (Use `@JsonIdentityInfo` if needed.)
- **Larger than binary.**
- **Slower than binary** — but usually fast enough.

The code examples here don't depend on Jackson (the repo has no Maven dependencies until module 05), but the markdown explains it because most production code will use it.

---

## Binary alternatives — Protobuf, Avro

When JSON's size and speed aren't enough, binary schemas win:

- **Protocol Buffers** (Google): schema in a `.proto` file, code generation, compact binary, fast. The defacto choice for service-to-service RPC (gRPC).
- **Avro** (Apache, popular in the Kafka / data-engineering world): schema is *part of* the serialized payload (or a registry), allowing schema evolution.
- **MessagePack** / **CBOR**: JSON-like models, binary encoding.

Trade-off: tooling-heavy (schema files, codegen). Worth it for hot inter-service paths and message queues.

---

## Performance

Native Java serialization is **slow** — easily 5-10x slower than JSON or Protobuf for the same payload, with larger output. Avoid it on the hot path. Caches and sessions that go through `ObjectOutputStream` will sit at the top of your CPU profile.

For a hot path: measure. JSON is usually fast enough; if it's not, go to Protobuf.

---

## Common pitfalls

- **Missing `serialVersionUID`.** Auto-generated SUID changes on any code edit. Old payloads stop deserializing.
- **Deserializing untrusted bytes.** Remote-code-execution risk.
- **`transient` on a field that's required at runtime.** Field is `null` after deserialization; downstream NPE.
- **Mutable static fields.** Not serialized. After deserialization in another process, the static state is whatever the new JVM's initial value is — typically null/0.
- **Circular references with JSON.** Default Jackson recursion explodes the stack. Either break the cycle or use `@JsonManagedReference` / `@JsonBackReference`.
- **Polymorphic Jackson typing enabled.** Brings back the security problem in JSON form. Don't enable globally; pick specific types.
- **Caches keyed by a serialized form.** Two equal objects can serialize to slightly different bytes (map iteration order, etc.), causing cache misses.

---

## Code examples

1. `JavaSerializeRoundTrip.java` — write a `Customer` to a file, read it back.
2. `TransientField.java` — show a `transient` password being null after deserialization.
3. `SerialVersionUidMismatch.java` — write with one SUID, attempt to read with another → `InvalidClassException`.
4. `ObjectGraphIsSerialized.java` — serializing one object pulls every reachable object with it.
5. `JsonByHandSimple.java` — a tiny "JSON" writer/reader using only `String.format` and `Scanner` (no library), to make the moving parts concrete since the repo has no Jackson dependency yet.

---

## Try this yourself

1. In `JavaSerializeRoundTrip.java`, add a new field to `Customer` (with a default), keep the SUID, re-serialize and re-read. Works. Now change the SUID and try to read the OLD `.ser` file. Watch the `InvalidClassException`.
2. In `TransientField.java`, change one of the fields you want to keep to `transient`. Watch the value go missing after the round-trip.
3. In `ObjectGraphIsSerialized.java`, mark the inner `Address` class non-serializable. The whole serialize call fails — try it.

---

## Self-check

1. Why is `transient` typically used on a `password` field, and what happens to that field's value after deserialization?
2. You ship an update that adds a new optional field to a `Serializable` class but you forget to set `serialVersionUID` explicitly. Old payloads in your cache won't deserialize. What's the actual reason, and what's the one-line fix?
3. Your colleague suggests accepting `ObjectInputStream` data from external HTTP requests for "convenience." Name two reasons that's dangerous.
