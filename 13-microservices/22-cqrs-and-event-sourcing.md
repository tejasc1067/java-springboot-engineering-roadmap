# CQRS and Event Sourcing

Two patterns that are often mentioned together and often confused. **CQRS** (Command Query Responsibility Segregation) means using one model to *change* data and a separate model to *read* it. **Event sourcing** means storing every change as an event, in an append-only log, and computing the current state from that log instead of storing the state itself. They fit well together, and each is useful on its own. Both cost more than a plain table and repository, so this topic is as much about when *not* to use them as how they work.

The demo is plain Java with no framework, so you can read every line of the mechanism.

---

## The problem this solves

**For CQRS:** one model is being asked to do two jobs that pull in opposite directions.

- *Writes* want a model that enforces the rules: "you can't withdraw more than the balance", "an order can't ship before it's paid". That model is usually normalised and organised around one aggregate at a time.
- *Reads* want data shaped for a screen: "my accounts, with owner, balance, and transaction count", "orders per customer, with totals, sorted". That's denormalised, often spanning several aggregates or services (topic 08).

Using one model for both means expensive joins and aggregation on every page view, or rule-checking code twisted around how the screens happen to look.

**For event sourcing:** a normal table stores only the *current* state. `balance = 80.00` tells you nothing about how it got there. When you later need the history ("what was the balance on the 3rd?", "show every change for the audit", "build a fraud report from all withdrawals"), it's gone. The `UPDATE` overwrote it.

---

## CQRS: separate the write model from the read model

```text
               commands (deposit, withdraw)                       queries (my accounts)
                         |                                                 ^
                         v                                                 |
   +--------------------------------------+            +--------------------------------+
   | WRITE side                           |   events   | READ side                      |
   | load -> check rules -> append events | ---------> | projection updates a view      |
   | (AccountCommands, Account)           |            | (AccountSummaryProjection)     |
   +--------------------------------------+            +--------------------------------+
```

- **Commands** change state and return almost nothing: success, failure, or a position (below). They never return a view.
- **Queries** read from a **read model** (also called a *projection* or *materialized view*). It's precomputed, shaped for one screen, and never used to make decisions.
- A **projection** listens to the events the write side produces and keeps the read model up to date.

In the demo, the read model is one summary per account:

```java
public record AccountSummary(String accountId, String owner, long balanceCents, int transactions) { }
```

`CqrsReadModelTest.readModel_isBuiltFromEvents_andShapedForTheScreen` runs three commands, lets the projection catch up, and reads `("acc-1", "Ann", 7500, 2)` in one lookup. Nothing is computed at read time.

CQRS doesn't require event sourcing. The write side can be an ordinary JPA entity that also publishes events, through the outbox (topic 19), to update a separate read table. Topic 08's materialized view across services is CQRS.

### Broken: projection lag (the user can't see their own write)

The read model is updated *after* the command commits. In production a background consumer does it, usually after a trip through a message broker. In between, the read model is stale. `projectionLag_userDoesNotSeeTheirOwnWrite`:

```java
commands.deposit("acc-1", 10_000);           // the command succeeded

summaries.find("acc-1").balanceCents();      // 0 - the projection hasn't run yet
```

The user deposits money, the page reloads from the read model, and the money "isn't there". They deposit again. This is eventual consistency (topic 07) showing up on your own screen, and it's the most common CQRS complaint.

### Fixed: read your own writes

The demo's commands return the **global position** where their event landed. The projection records the last position it processed. Together, the caller can tell whether the view includes its own write (`readYourOwnWrites_byWaitingForTheCommandsPosition`):

```java
long position = commands.deposit("acc-1", 10_000);

summaries.hasProcessed(position);   // false: show "updating..." or wait briefly
// ... projection catches up ...
summaries.hasProcessed(position);   // true: now the view includes the deposit
```

Other common fixes:

- Return what the user needs **from the command itself** ("deposit accepted, new balance 100.00", computed on the write side).
- **Optimistically update the UI** with the change the user just made.
- For the screens that truly need it, **read from the write side**. Not every query has to go through the read model.

Pick per screen. A dashboard can be seconds behind. "Did my payment go through?" can't.

---

## Event sourcing: the log is the source of truth

With event sourcing, the write side stores **no current state at all**. There's no `accounts` table with a `balance` column, only events:

```java
public sealed interface AccountEvent {
    record Opened(String owner) implements AccountEvent { }
    record Deposited(long cents) implements AccountEvent { }
    record Withdrawn(long cents) implements AccountEvent { }
}
```

Events are named in the past tense because they are facts that already happened. Once appended, they are **never changed or deleted**.

To handle a command, you **load** the aggregate by replaying its events, **decide** using that state, and **append** new events:

```java
public static Account replay(List<StoredEvent> stream) {
    Account account = new Account();
    stream.forEach(stored -> account.apply(stored.event()));
    return account;
}

public List<AccountEvent> withdraw(long cents) {
    if (cents > balanceCents) throw new IllegalStateException("insufficient funds: ...");
    return List.of(new AccountEvent.Withdrawn(cents));   // returns events, changes nothing itself
}
```

`EventSourcingTest` shows what you get:

- **`currentState_isTheReplayOfEveryEvent`**: four events (opened, +100.00, −25.00, +5.00) replay to a balance of 80.00.
- **`anyPastState_canBeRebuilt`**: replay only the first three events to get the balance *as it was then* (75.00). "What did the system believe at 14:02?" has a real answer.
- **`rulesAreCheckedAgainstReplayedState_andRejectedCommandsLeaveNoEvent`**: an overdraft attempt throws, and nothing is appended. The log contains only things that really happened.

And from `CqrsReadModelTest`:

- **`aNewReadModel_canBeBuiltLaterFromTheWholeHistory`**: months later, the fraud team wants "largest single withdrawal per account". With only current balances stored, that history wouldn't exist. Here it's one pass over the log into a brand-new view. **New read models can be built from the past**, which is the biggest practical benefit of event sourcing.

### Broken: two writers, no version check

Two requests withdraw 80.00 from an account holding 100.00 at the same moment. Each loads the account, each sees 100.00, and each approves its withdrawal. `ConcurrencyTest.withoutAVersionCheck_twoWithdrawalsBothPass_andTheAccountGoesNegative`:

```java
Account seenByA = commands.load("acc-1");     // balance 100.00, version 2
Account seenByB = commands.load("acc-1");     // balance 100.00, version 2

store.appendWithoutVersionCheck("acc-1", seenByA.withdraw(8_000));
store.appendWithoutVersionCheck("acc-1", seenByB.withdraw(8_000));

// balance: -60.00
```

The rule was checked, twice, against state that was already out of date.

### Fixed: append with an expected version

Every append says which version of the stream it decided on. If the stream has moved on since then, the append is rejected:

```java
public synchronized long append(String accountId, int expectedVersion, List<AccountEvent> events) {
    int currentVersion = streamOf(accountId).size();
    if (currentVersion != expectedVersion) {
        throw new ConcurrencyException("account " + accountId + " is at version " + currentVersion
                + ", expected " + expectedVersion);
    }
    ...
}
```

`withAnExpectedVersion_theSecondWithdrawalIsRejected`: A's append moves the stream to version 3, and B's append (expecting 2) throws `ConcurrencyException`. B's caller reloads, decides again, and the rule now refuses the withdrawal. The balance ends at 20.00. This is optimistic locking, the same idea as JPA's `@Version` (module 10), applied to a stream of events. In a database-backed event store it's a unique constraint on `(stream_id, version)`.

---

## The costs (read before adopting)

Event sourcing in particular is a large commitment. Take it on knowing these costs:

- **Events are forever.** You can't fix a wrongly shaped event with a migration. Old events must stay readable forever, so you need *upcasting* (converting old event versions to the new shape as they're read) and real discipline about event design.
- **Replay gets slow.** An account with 50,000 events takes 50,000 applications to load. The usual fix is a **snapshot**: store the state every N events and replay only what came after it. That's more machinery.
- **Eventual consistency everywhere.** Every read model lags. Every screen needs a decision about staleness (above).
- **Projections can break.** A bug in a projection means fixing the code and rebuilding the view by replaying the log from position 0. That's possible, because the log is the truth, but it takes operational work and time.
- **Personal data.** "Never delete events" collides with the right to have personal data erased. A common approach is to encrypt personal data per user and throw away the key, sometimes called *crypto-shredding*. Plan it up front.
- **The learning curve.** Most developers have never worked this way. Simple CRUD screens become commands, events, and projections.

---

## When to use them / when not to

| | Use it when | Don't use it when |
|---|---|---|
| **CQRS** | Reads and writes have very different shapes or loads (complex dashboards over simple writes; reads spanning services, topic 08) | A screen maps closely onto a table. A repository and a DTO (module 09) are enough. |
| **Event sourcing** | History *is* the domain: ledgers, accounts, audit-heavy workflows, "what did we know when?". Or you'll keep needing new views over past behaviour. | CRUD data, such as user profiles, settings, product catalogues. It adds all the costs above for no benefit. |

Apply them to the **one bounded context** that needs them (topic 03), not the whole system. A typical system might event-source its ledger, use CQRS for a reporting dashboard, and keep plain CRUD everywhere else.

Some tools you'll meet: **Axon Framework** (Java CQRS and event sourcing framework), **EventStoreDB** (a database built for event streams), and **Kafka**, often used to carry events to projections (module 16). Kafka can retain events for a long time, but it is a log for distributing events, not an event store with per-stream version checks. Don't use it as one without understanding that difference.

---

## Common pitfalls

- **No expected-version check on append.** Two concurrent commands both pass the rules against stale state (`ConcurrencyTest`). Always append with the version you loaded.
- **Reading the read model right after a command and trusting it.** It lags. Return results from the command, wait for the command's position, or read from the write side for that screen.
- **Using a read model to make write-side decisions.** It's stale by design. Rules are checked only against the replayed aggregate.
- **Events that describe changes to the database instead of business facts.** `AccountBalanceUpdated(80.00)` loses the meaning. `Withdrawn(20.00)` keeps it, and new projections can be built from it.
- **Event sourcing everything.** Reserve it for the parts of the domain where history matters.

---

## Code examples

`CODE_EXAMPLES/22-cqrs-and-event-sourcing/cqrs-event-sourcing-demo/`. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/cqrs/AccountEvent.java` — the events: past-tense facts.
2. `src/main/java/com/example/cqrs/EventStore.java` — the append-only log: `append` with an expected version, `appendWithoutVersionCheck`, and `readAllAfter(position)`.
3. `src/main/java/com/example/cqrs/Account.java` — the aggregate: `replay`, `apply`, and commands that return events.
4. `src/main/java/com/example/cqrs/AccountCommands.java` — the write side: load, decide, append.
5. `src/test/java/com/example/cqrs/EventSourcingTest.java` — replay, past state, rejected commands.
6. `src/test/java/com/example/cqrs/ConcurrencyTest.java` — the overdraft without a version check, then the rejected stale append.
7. `src/main/java/com/example/cqrs/AccountSummaryProjection.java` — the read side: `catchUp`, `hasProcessed(position)`.
8. `src/test/java/com/example/cqrs/CqrsReadModelTest.java` — the read model, projection lag, read-your-own-writes, and a new view built from history.

---

## Try this yourself

1. Add a `Closed` event and a `close()` command that's only allowed when the balance is zero. Make `deposit` and `withdraw` refuse on a closed account. Which classes did you change, and did the projection need to change?
2. Add snapshots: every 3 events, store `(version, balance)` in a map. Make `load` start from the latest snapshot and replay only later events. Write a test proving the balance matches a full replay.
3. Build a second projection, "total deposited per owner", and fill it by calling `readAllAfter(0)` on a store that already has history. Why was this possible without touching the write side?

## Self-check

1. What is the difference between CQRS and event sourcing? Describe a system that uses CQRS without event sourcing.
2. Two requests withdraw 80.00 from a 100.00 account at the same time. Walk through how an event store with expected-version appends stops the second, and what that request's caller should do next.
3. A user deposits money and the "my accounts" page doesn't show it. Explain why, and give two different fixes.
4. Name three costs of event sourcing that a plain table doesn't have, and one kind of data you would never event-source.
