# 11 — Transactions and ACID Properties

A **transaction** is a group of database operations that succeed together or fail together. No half-states.

This topic explains *what* a transaction is conceptually. Topic 15 shows how to control transactions in Java with JDBC.

---

## The motivating example: a bank transfer

Alice transfers $100 to Bob. Two operations:

```sql
UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice';
UPDATE accounts SET balance = balance + 100 WHERE owner = 'Bob';
```

Imagine the server crashes between statement 1 and statement 2. Without a transaction:

- $100 has been deducted from Alice's account.
- $100 has *not* been added to Bob's account.
- $100 has vanished into the void.

The whole point of databases is that this **must not be possible**. A transaction wraps both statements into a single unit:

```sql
BEGIN;
    UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice';
    UPDATE accounts SET balance = balance + 100 WHERE owner = 'Bob';
COMMIT;
```

Now one of two things happens:

- Both updates apply (COMMIT succeeds) → money moved correctly.
- Neither update applies (crash, error, ROLLBACK) → no change to either account.

Half-states are impossible.

---

## The three TCL commands

| Command | What it does |
|---------|---------|
| `BEGIN` (or `START TRANSACTION`) | Start a transaction. Subsequent statements are tentative. |
| `COMMIT` | Make all tentative changes permanent. Other connections see them. |
| `ROLLBACK` | Discard all tentative changes. As if they never happened. |

In JDBC the equivalents are `conn.setAutoCommit(false)`, `conn.commit()`, `conn.rollback()`. More on this in topic 15.

---

## ACID — the four guarantees

Transactions provide four guarantees, abbreviated **ACID**. Almost every interview asks about these.

### A — Atomicity

"All or nothing." Either every statement in the transaction succeeds, or none of them do. No partial application.

In the bank transfer: both updates happen, or neither does. Never one without the other.

### C — Consistency

The database moves from one valid state to another. Any constraints (`UNIQUE`, `NOT NULL`, `CHECK`, foreign keys) are still satisfied after the transaction.

If a transaction would leave the database invalid (e.g. violate a `CHECK (balance >= 0)`), it's rolled back. Bank transfer can't leave Alice with -$50.

(Note: "consistency" here means *database constraints stay valid*, not "distributed consistency" — different concept in distributed systems.)

### I — Isolation

Concurrent transactions don't interfere with each other. If Alice transfers $100 to Bob *at the same time* that someone reads Bob's balance, the reader sees either the old balance or the new — never some half-applied intermediate state.

Isolation is the trickiest property and has *levels* — see below.

### D — Durability

Once a transaction is committed, it survives crashes. If the database server loses power one microsecond after `COMMIT`, the change is still there when the server restarts.

Durability is achieved via the **write-ahead log**: before changes are written to data files, they're written to a sequential log. On recovery, the log is replayed.

---

## Isolation levels

Isolation is the hardest property to guarantee fully — it can be expensive. So most databases offer a *menu* of isolation levels, trading guarantees for performance.

Listed from weakest (fastest) to strongest (slowest):

| Level | What can go wrong |
|-------|-------------------|
| **Read uncommitted** | You can read another transaction's uncommitted changes (a "dirty read"). |
| **Read committed** (most common default) | No dirty reads, but two reads of the same row in your transaction may see different values (a "non-repeatable read"). |
| **Repeatable read** | Within your transaction, the same query always returns the same rows. New rows from other transactions can still appear (a "phantom read"). |
| **Serializable** | Strongest. Transactions behave as if they ran one at a time. Slowest. |

### Concrete examples of the anomalies

**Dirty read** (read uncommitted):
```
T1: BEGIN; UPDATE balance SET amount = 0 WHERE owner = 'Alice';   -- not committed
T2: SELECT amount FROM balance WHERE owner = 'Alice';            -- reads 0
T1: ROLLBACK;                                                     -- Alice's balance never actually changed
                                                                  -- T2 made decisions based on phantom data
```

**Non-repeatable read** (read committed):
```
T1: BEGIN; SELECT amount FROM balance WHERE owner = 'Alice';     -- reads 1000
T2: UPDATE balance SET amount = 500 WHERE owner = 'Alice'; COMMIT;
T1: SELECT amount FROM balance WHERE owner = 'Alice';             -- now reads 500, same transaction
```

**Phantom read** (repeatable read):
```
T1: BEGIN; SELECT COUNT(*) FROM orders WHERE customer = 'Alice'; -- reads 3
T2: INSERT INTO orders ...; COMMIT;
T1: SELECT COUNT(*) FROM orders WHERE customer = 'Alice'; -- now reads 4
```

**Defaults in practice:**

- PostgreSQL, Oracle, SQL Server: `Read committed`
- MySQL InnoDB: `Repeatable read`
- H2: `Read committed`

You can change the level per session if you need stronger guarantees. **For most apps, the default is fine** — pushing to `serializable` can dramatically reduce throughput.

---

## What's *not* in ACID

Some confusions worth pre-empting:

- ACID doesn't guarantee performance. A "consistent" database can still be slow.
- ACID doesn't prevent two transactions from racing to update the same row — one will win, the other gets rolled back or blocked. (Optimistic vs pessimistic locking, beyond scope here.)
- ACID is per-database. A "transaction" across two databases (e.g. write to MySQL + send to Kafka) is *not* atomic. Distributed transactions are hard.

---

## When you don't need a transaction

A single SQL statement is **implicitly its own transaction**. You don't need `BEGIN` around a single `UPDATE`.

You need explicit transactions when:

- Two or more statements must succeed together (transfer, multi-step business operation).
- You want to verify state, then act on it, without another connection sneaking in (e.g. "check inventory, then create order").

---

## Code examples

1. `NoTransactionPartialFailure.java` — runs a two-step "transfer" with autocommit on. Forces a failure between steps and shows the database left in an inconsistent state.
2. `WithTransactionRolledBack.java` — same scenario, but wrapped in a transaction. Failure triggers ROLLBACK. Database is unchanged.
3. `AtomicityDemo.java` — shows that all statements in a committed transaction take effect together.
4. `IsolationLevels.java` — sets different isolation levels and observes the difference.

(These previews topic 15's JDBC transaction syntax; full coverage comes there.)

---

## Try this yourself

1. In `NoTransactionPartialFailure.java`, comment out the forced failure. Does the transfer complete correctly?
2. In `WithTransactionRolledBack.java`, replace `conn.rollback()` with `conn.commit()` inside the catch block. What happens to the data?
3. In `IsolationLevels.java`, try changing to `Connection.TRANSACTION_SERIALIZABLE`. Note any timing or behavior differences.

---

## Self-check

1. What does each letter in ACID stand for, in one sentence each?
2. Alice and Bob's bank transfer crashes halfway. What guarantee is responsible for ensuring no money vanishes?
3. Two transactions reading the same row at the same time: which ACID property ensures they don't see each other's uncommitted changes?
