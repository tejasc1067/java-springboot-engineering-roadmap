# session-demo

Two instances of one Spring Boot app, a shared "browser" (one cookie store), and the question: after logging in on
instance A, is the user still logged in when the next request lands on instance B?

- `InMemorySessionTest` — Spring Session switched off, so sessions live in each instance's Tomcat. Login on A,
  then `/me` on B returns 401. A non-`Serializable` session attribute works fine here, which is why that bug hides.
- `RedisSessionTest` — `spring-session-data-redis` on the classpath. Login on A is visible on B; logout on B logs
  the user out on A; the session is a Redis hash `spring:session:sessions:<id>` whose TTL is the session timeout;
  the non-`Serializable` attribute now fails the request with 500.

Run (Docker running):

```
mvn -q test
```

`TwoInstances` starts the app twice on random ports with `SpringApplicationBuilder`. Because those aren't
`@SpringBootTest` contexts, `RedisSessionTest` passes the container's host and port as properties instead of using
`@ServiceConnection`. The test prints a `SerializationException` stack trace: that's the 500 it expects.
