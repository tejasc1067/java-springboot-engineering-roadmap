# scopes-demo

Singleton vs prototype, plus the classic "prototype dependency in a singleton" trap and the `ObjectProvider` fix.

Files:

- `Counter` -- default scope (singleton). Shared mutable state to make the sharing obvious.
- `WorkTicket` -- `@Scope("prototype")`. Each instance gets a unique UUID.
- `BrokenCoordinator` -- singleton that injects `WorkTicket` directly. The trap.
- `FixedCoordinator` -- singleton that injects `ObjectProvider<WorkTicket>`. The fix.

Run:

```bash
mvn -q test
mvn -q compile exec:java
```

The test class `ScopesTest` walks through all four behaviors with assertions, including a passing test that documents the broken-coordinator bug as observed (not as a failure).
