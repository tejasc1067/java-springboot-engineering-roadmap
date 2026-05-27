# mockito-basics-demo

Testing `OrderService` in isolation by replacing its two collaborators (`UserRepository`, `OrderRepository`) with Mockito mocks.

Run the tests:

```
mvn test
```

You'll see a warning about Mockito "self-attaching" — that's expected on Java 21+ and harmless; topic 08's notes explain it. The three tests cover the active-user happy path, the unknown user, and the inactive user.
