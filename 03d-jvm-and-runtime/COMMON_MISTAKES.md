# Common Mistakes — Module 03d (JVM, Runtime & Production)

Memory leaks, GC misconceptions, reflection traps, serialization vulnerabilities, networking pitfalls, JVM tuning mistakes. The bugs you'll meet in production.

---

## 1. Static cache that never evicts (topic 01)

```java
static final Map<String, byte[]> cache = new HashMap<>();
public void serve(String k, byte[] v) { cache.put(k, v); }
```

The cache grows forever. Heap usage climbs daily. OOM in a week or a month.

**Fix.** Bounded cache with eviction: Caffeine `Cache` with `maximumSize` and `expireAfterWrite`. Or `LinkedHashMap` configured as LRU.

---

## 2. Raising `-Xmx` to "fix" an OOM (topics 01, 02)

The leak is still there. You're buying days, not solving anything. The OOM returns at a higher water mark, harder to investigate (a bigger heap dump takes longer to analyze).

**Fix.** Take a heap dump (`jcmd <pid> GC.heap_dump file.hprof`). Open in Eclipse MAT. Find the dominator. Fix the leak.

---

## 3. `System.gc()` in code (topic 02)

It's a hint, not a command. Even when it does fire, you pinned a full-GC pause at whatever moment your code ran — often the wrong moment.

**Fix.** Remove the call. If you're worried about GC, log GC (`-Xlog:gc*`) and look at the actual data.

---

## 4. Annotation declared without `@Retention(RUNTIME)` (topic 06)

```java
@Target(METHOD)
public @interface MyAnno { }
```

Default retention is `CLASS` — present in `.class`, **invisible to reflection**. Your framework's `m.isAnnotationPresent(MyAnno.class)` returns `false`. You spend two hours wondering why nothing's being scanned.

**Fix.** `@Retention(RetentionPolicy.RUNTIME)`. Every annotation a framework reads.

---

## 5. `Serializable` class without `serialVersionUID` (topic 07)

The compiler auto-generates the SUID from a hash of the class signature. Any code change — adding a field, renaming a method — changes the hash, breaks deserialization of every existing payload.

**Fix.** Declare `private static final long serialVersionUID = 1L;` on every `Serializable` class. Bump only on intentional breaking changes.

---

## 6. Deserializing untrusted bytes (topic 07)

`ObjectInputStream.readObject(networkBytes)`. Classic Java RCE: a crafted payload instantiates a chain of objects whose `readObject` methods ultimately call `Runtime.exec`.

**Fix.** Never deserialize untrusted Java-native bytes. Use JSON or Protobuf for any cross-process or cross-trust-boundary message. If you must, use `ObjectInputFilter` (Java 9+) to whitelist allowed classes.

---

## 7. `Socket.read` with no `setSoTimeout` (topic 08)

```java
Socket s = new Socket(host, port);
String line = new BufferedReader(...).readLine();   // blocks forever if peer never responds
```

A dead or slow peer hangs your handler thread until the OS gives up — could be hours.

**Fix.** `s.setSoTimeout(5000)` (or whatever your SLO allows) before any read.

---

## 8. Assuming `read` returns a whole message (topic 08)

```java
byte[] buf = new byte[8192];
int n = in.read(buf);                  // got SOME bytes; maybe not all
String msg = new String(buf, 0, n);    // partial message
```

TCP is a stream of bytes; one `read` can give you part of one message, or the end of one plus the start of another.

**Fix.** Define a framing protocol: length-prefixed, delimited (`\n`), or fixed-size.

---

## 9. NIO `ByteBuffer` without `flip()` (topic 09)

```java
buf.put(...);
buf.put(...);
buf.get();                  // reads garbage past position
```

`position` is past your data. `get()` reads from there.

**Fix.** `buf.flip()` between writing and reading. Always. Most ByteBuffer bugs are this.

---

## 10. Default `-Xmx` in a Docker container (topic 10)

Pre-Java 10 the JVM didn't know about cgroups; it sized `-Xmx` based on host RAM. Container had 2GB; JVM thought it had 256GB; OOM-killer fired when the JVM grew past the cgroup limit.

**Fix.** Always set `-Xmx` explicitly, or use `-XX:MaxRAMPercentage=75` on Java 10+. Match the container limit, not the host.

---

## 11. One global thread pool for everything (topic 11)

Latency-sensitive request handling and overnight batch jobs share `commonPool`. The batch fills the queue; requests sit waiting.

**Fix.** Separate pools per workload class. Request pool, batch pool, scheduled-job pool, each sized for its workload.

---

## 12. Tuning JVM flags without measuring (topics 10, 11)

Adding `-XX:+UseG1GC`, `-XX:MaxGCPauseMillis=50`, `-Xms4g`, all at once. Nothing changes; you can't tell which flag did what.

**Fix.** Change one flag at a time. Run for a representative period. Compare GC logs and latency metrics. Move on or revert. JFR is your friend.

---

## 13. Performance "optimization" without a profile (topics 04, 11)

You rewrote a loop to use a `for` index instead of an iterator. Saved 200 nanoseconds per request. The endpoint also makes a 50ms DB call you didn't touch. Net change: zero, but harder to read.

**Fix.** Profile first. JFR + flame graphs. Look for the *actual* hot method. Then optimize. Then re-profile to confirm.
