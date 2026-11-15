# cqrs-event-sourcing-demo

CQRS and event sourcing in plain Java, small enough to read in full:

- `EventStore` — an append-only log; `append` checks the expected stream version, `appendWithoutVersionCheck` doesn't.
- `Account` — an event-sourced aggregate: state rebuilt by replay, commands return new events.
- `AccountCommands` — the write side: load, decide, append. Returns the event's global position.
- `AccountSummaryProjection` — the read side: a view built from events, updated only when `catchUp` runs.

Tests:

- `EventSourcingTest` — current state is the replay of all events; any past state can be rebuilt; a rejected command leaves no event.
- `ConcurrencyTest` — without a version check, two 80.00 withdrawals from 100.00 both succeed (balance -60.00); with one, the second is rejected.
- `CqrsReadModelTest` — the read model is built from events; projection lag hides the user's own deposit; waiting for the command's position fixes it; a new view is built later from the whole history.

Run:

```
mvn -q test
```
