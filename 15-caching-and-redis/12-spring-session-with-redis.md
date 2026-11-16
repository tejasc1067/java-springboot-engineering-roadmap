# Spring Session with Redis

A server-side session (module 12 topic 06) keeps "this browser is logged in as ann" in the memory of the server that handled the login. Run two instances behind a load balancer and the next request may land on the *other* instance, which has never heard of that session: the user is suddenly logged out. Spring Session fixes this by moving the session out of the server's memory into a shared store, here Redis, without changing any code that uses `HttpSession`.

---

## The problem this solves

```text
browser --POST /login--> load balancer --> instance A   creates session 7F3A in A's memory, sets cookie JSESSIONID=7F3A
browser --GET /me------> load balancer --> instance B   looks up 7F3A in B's memory: nothing -> 401
```

`InMemorySessionTest.loginOnA_isUnknownToB_soTheUserIsLoggedOutOnTheirNextRequest` runs the same app twice on two ports and uses one HTTP client with one cookie store as the browser:

```java
app.post(app.a, "/login?user=ann");

app.get(app.a, "/me").body();          // "ann on A"
app.get(app.b, "/me").statusCode();    // 401 - same cookie, but B has never seen that session id
```

Users see random logouts, forms that lose their contents, and carts that empty themselves, depending on which instance each request happened to hit. It also happens with a single instance: every restart or deploy wipes all sessions, logging everyone out.

---

## How it works

Add two dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

Spring Boot sees Spring Session on the classpath and registers a servlet filter, `SessionRepositoryFilter`, in front of everything else. It wraps each request so that `request.getSession()` returns a session loaded from Redis, and saves any changes back before the response is sent. The controller is unchanged:

```java
@PostMapping("/login")
String login(@RequestParam String user, HttpSession session) {
    session.setAttribute("user", user);
    return "logged in as " + user + " on " + instanceName;
}
```

`RedisSessionTest.loginOnA_isVisibleOnB_becauseTheSessionIsInRedis`: log in on A, call `/me` on B, get `"ann on B"`. `logoutOnB_logsTheUserOutOnA_too`: `session.invalidate()` on B deletes the session from Redis, so A returns 401 too. An instance can now be restarted, added, or removed without logging anybody out.

Two visible differences:

- The cookie is called **`SESSION`**, not `JSESSIONID`, and its value is the session id Base64-encoded.
- Every request that uses the session costs a Redis round trip: one read at the start, and a write if anything changed or to update the last-access time.

### What's stored in Redis

`theSessionIsARedisHash_withTheAttributesAndATtlFromTheSessionTimeout`. One **hash** per session:

```text
HKEYS spring:session:sessions:5f0c9d1e-...
  creationTime
  lastAccessedTime
  maxInactiveInterval
  sessionAttr:user          <- one field per attribute you set

TTL spring:session:sessions:5f0c9d1e-...
  900                       <- server.servlet.session.timeout: 15m
```

The TTL is the session timeout and is reset on each request that touches the session, so an idle session expires on its own in Redis. Logging out deletes the key.

This is Spring Boot's default Redis repository. Setting `spring.session.redis.repository-type=indexed` switches to one that also keeps an index of sessions per user (to list or end all of a user's sessions) and publishes session-expired events, at the cost of extra keys and Redis key-space notifications.

### Broken: a session attribute that isn't `Serializable`

In memory, a session attribute is just a Java object reference. In Redis it must be turned into bytes, and Spring Session's default serializer is Java serialization, which requires `java.io.Serializable`. `Cart` doesn't implement it.

```java
public class Cart {                     // no "implements Serializable"
    private final List<String> items = new ArrayList<>();
    ...
}
session.setAttribute("cart", cart);
```

- `InMemorySessionTest.nonSerializableAttribute_worksFineInMemory_soTheBugHidesUntilSessionsMoveToRedis`: 200. Nothing complains for as long as sessions stay in memory.
- `RedisSessionTest.nonSerializableAttribute_failsTheRequest_onceSessionsAreStoredInRedis`: 500, with

```text
SerializationException: Cannot serialize
  ... DefaultSerializer requires a Serializable payload but received an object of type [com.example.session.Cart]
```

The controller method finished; the failure happens afterwards, when the filter saves the session. The day you add Spring Session, every endpoint that stores a non-serializable object starts failing.

**Fix:** make every session attribute `Serializable` (`SerializableCart`), or register a JSON serializer bean named `springSessionDefaultRedisSerializer`. Either way, store small things in the session: ids and short values, not whole entity graphs. Java-serialized data also breaks when the class changes between deploys (a renamed field), and JSON is easier to read in Redis.

---

## The alternatives

### Sticky sessions

The load balancer remembers which instance served a browser and keeps sending it there (by a cookie or a hash of the client IP). Sessions stay in memory and the problem seems to go away. It doesn't:

- When that instance restarts, crashes, or is removed during a scale-down or a deploy, all its users are logged out.
- Load spreads unevenly: a busy user stays on one instance, and new instances get only new users.
- It ties correctness to a load-balancer setting that someone can change without knowing.

Sticky sessions are acceptable as a performance optimisation on top of a shared store, not as a substitute for one.

### No session at all: stateless tokens

With a JWT (module 12 topic 08), the token itself carries who the user is and the server stores nothing, so any instance can check it with no shared store. The costs are on the other side: a token can't easily be revoked before it expires, it grows with every claim you put in it, and it's sent on every request.

| | Session in Redis | Stateless JWT |
|---|---|---|
| Server stores | session data, in Redis | nothing (just the signing key) |
| Log out / revoke now | delete the key | hard: wait for expiry, or keep a deny list (which is a store again) |
| Every request costs | a Redis read | verifying a signature |
| Natural fit | browser apps with server-rendered pages or a backend-for-frontend; form login | APIs called by mobile apps, SPAs with tokens, other services |
| CSRF | needed (cookie sent automatically) | not needed if the token is in a header |

Pick sessions-in-Redis when the client is a browser and you want instant logout and server-side state; pick tokens for APIs and service-to-service calls. [12-security/06-form-login-vs-basic-vs-stateless.md](../12-security/06-form-login-vs-basic-vs-stateless.md) covers the same choice from the security side.

---

## When to use it / when not to

- **Use it** when an app with server-side sessions (form login, a shopping cart in the session, multi-step forms) runs as more than one instance, or must survive restarts without logging users out.
- **Don't add it** to a stateless API that has no sessions. Check that nothing creates sessions by accident (`SessionCreationPolicy.STATELESS` in module 12).
- **Redis is now on the login path.** If Redis is down, every request that needs a session fails. Run Redis with replication, and alert on it like you would the database.

---

## Common pitfalls

- **Assuming sticky sessions solve it.** They do until an instance restarts. Share the sessions.
- **Non-`Serializable` attributes.** They work in memory and fail with a 500 the day sessions move to Redis. Make them `Serializable` or use a JSON serializer.
- **Big objects in the session.** Every request reads them from Redis. Store ids; load the rest when needed.
- **Changing a session class between deploys.** Old sessions in Redis can no longer be deserialized and users get errors. Keep session classes stable, or use JSON with tolerant reading.
- **Mixing Redis keyspaces carelessly.** Sessions share the Redis instance with caches; an eviction policy like `allkeys-lru` that evicts cache entries under memory pressure will also evict sessions, logging users out. `volatile-lru` doesn't help: sessions have a TTL, so they're candidates too. Give sessions their own Redis with `maxmemory-policy noeviction`.

---

## Code examples

`CODE_EXAMPLES/12-spring-session-with-redis/session-demo/`. Run with `mvn -q test` (Docker running). Read in this order:

1. `src/main/java/com/example/session/AccountController.java` — login, `/me`, logout, and the two cart endpoints.
2. `src/test/java/com/example/session/TwoInstances.java` — two copies of the app on random ports, and one shared cookie store as the browser.
3. `src/test/java/com/example/session/InMemorySessionTest.java` — sessions in Tomcat's memory: B doesn't know the user; the non-serializable cart works.
4. `pom.xml` — the two dependencies that are the whole fix.
5. `src/test/java/com/example/session/RedisSessionTest.java` — sessions in Redis: login on A seen on B, logout everywhere, the hash and its TTL, and the 500 from the non-serializable cart.
6. `src/main/java/com/example/session/Cart.java` and `SerializableCart.java` — the broken and fixed attribute.

---

## Try this yourself

1. In `RedisSessionTest`, after logging in, use `redis.delete(key)` on the session key, then call `/me`. What happens, and which production event does that simulate?
2. Set `server.servlet.session.timeout` to `2s` with an extra command-line argument in one test, log in, wait 3 seconds with Awaitility, and check `/me`. Is the key still in Redis?
3. Register a `GenericJackson2JsonRedisSerializer` bean named `springSessionDefaultRedisSerializer` and re-run the tests. Does `/cart/broken` still fail? Look at the stored value with `redis.opsForHash().get(key, "sessionAttr:user")`.

## Self-check

1. A user logs in, clicks a link, and is asked to log in again, but only sometimes. The app runs as 3 instances. What's happening, and why do sticky sessions only partly fix it?
2. You add `spring-session-data-redis` and one endpoint starts returning 500 with `DefaultSerializer requires a Serializable payload`. Why did it work before, and what are two fixes?
3. Your team must choose between sessions in Redis and stateless JWTs for a new browser app that needs "log out of all devices". Which do you pick, and what does the other choice cost?
