# 15 — JDBC Transaction Management

Topic 11 explained transactions conceptually. This topic shows the JDBC API for controlling them, plus the patterns you'll actually use in real code.

---

## The default: autocommit is ON

Open a JDBC connection and run a statement — by default it commits immediately, on its own. Each statement is its own transaction.

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     Statement stmt = conn.createStatement()) {
    stmt.executeUpdate("UPDATE accounts SET balance = balance - 100 WHERE owner = 'Alice'");
    // ↑ already committed. Permanent. You can't undo it from here.
}
```

This is fine for one-shot operations. It's catastrophic for multi-step business logic — if the second step fails, the first has already happened.

To group multiple statements into one transaction, you turn autocommit OFF.

---

## The pattern: turn autocommit off, commit or rollback explicitly

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
    conn.setAutoCommit(false);                                       // ① start the transaction
    try {
        try (PreparedStatement debit = conn.prepareStatement(...)) { /* run */ }
        try (PreparedStatement credit = conn.prepareStatement(...)) { /* run */ }
        conn.commit();                                               // ② both succeeded → permanent
    } catch (Exception e) {
        conn.rollback();                                             // ③ failure → undo everything
        throw e;
    } finally {
        conn.setAutoCommit(true);                                    // ④ restore default
    }
}
```

This is the canonical multi-step transaction pattern in JDBC. Memorize the shape.

---

## What the methods do

| Method | What it does |
|--------|--------------|
| `conn.setAutoCommit(false)` | Don't auto-commit. Statements become tentative. |
| `conn.commit()` | Make all tentative changes permanent. End the transaction. |
| `conn.rollback()` | Discard all tentative changes since the last commit. End the transaction. |
| `conn.setAutoCommit(true)` | Restore default. Each statement auto-commits again. |

Once you call `commit()` or `rollback()`, the transaction is over. The next statement starts a new one (if autocommit is still off).

---

## Where to put the try/catch

The shape above looks verbose, but every part matters:

- The outer `try-with-resources` ensures the connection always closes.
- The inner `try` covers the work that must succeed-or-be-rolled-back.
- The `catch` rolls back on any failure (don't catch and swallow — re-throw so the caller knows).
- The `finally` resets autocommit so future code using this connection isn't surprised.

You'll write this shape many times. Some teams wrap it in a helper:

```java
<T> T inTransaction(Connection conn, Callable<T> work) throws Exception {
    conn.setAutoCommit(false);
    try {
        T result = work.call();
        conn.commit();
        return result;
    } catch (Exception e) {
        conn.rollback();
        throw e;
    } finally {
        conn.setAutoCommit(true);
    }
}
```

Then:

```java
inTransaction(conn, () -> {
    debit(conn, fromAccount, amount);
    credit(conn, toAccount, amount);
    return null;
});
```

Spring's `@Transactional` annotation is essentially a fancier version of this helper. When you get to Spring, you'll understand what it's doing for you.

---

## Savepoints (partial rollback)

A **savepoint** is a marker inside a transaction. You can roll back to a savepoint without rolling back the entire transaction.

```java
conn.setAutoCommit(false);
try (PreparedStatement insertOrder = ...; PreparedStatement insertItem = ...) {
    insertOrder.executeUpdate();         // step 1: create order
    Savepoint afterOrder = conn.setSavepoint("after_order");

    try {
        insertItem.executeUpdate();      // step 2: try to add an item
    } catch (SQLException e) {
        conn.rollback(afterOrder);       // roll back step 2 only; keep step 1
    }
    conn.commit();
}
```

Useful for "best-effort" sub-steps. In practice, savepoints are uncommon in everyday CRUD apps — most logic is either "all-or-nothing" or split across multiple transactions.

---

## Common mistakes

### 1. Forgetting to commit

```java
conn.setAutoCommit(false);
stmt.executeUpdate("UPDATE ...");
// no commit; method returns; connection closes
```

What happens depends on the driver. Most drivers ROLLBACK on close — so your update silently vanishes. Always commit or explicitly rollback.

### 2. Catching the exception but not rolling back

```java
try {
    stmt.executeUpdate("UPDATE A ...");
    stmt.executeUpdate("UPDATE B ...");
    conn.commit();
} catch (Exception e) {
    log.error("oops", e);  // ← no rollback! Partial state may be visible until close
}
```

If the exception happened *after* the first update succeeded, you have partial state hanging. Always call `rollback()` in the catch.

### 3. Long-running transactions

Holding a transaction open for a long time (e.g., across a slow API call) holds locks the whole time, blocking other transactions. **Transactions should be short.** Do the slow work outside the transaction; the transaction only wraps the database operations themselves.

### 4. Mixing autocommit and explicit transactions on the same connection

If you `setAutoCommit(false)` then forget to set it back to `true` (or vice versa), the next code using this connection has surprising behavior. Always restore the autocommit state in `finally`.

---

## A note on connection-per-transaction

In production with a connection pool (topic 17), each transaction usually borrows a connection from the pool, does its work, commits/rolls back, and returns the connection. The lifecycle: **borrow → setAutoCommit(false) → work → commit/rollback → setAutoCommit(true) → return.**

Spring `@Transactional` does this for you. The principle is the same as the manual pattern.

---

## Code examples

1. `AutoCommitDefault.java` — shows that statements commit immediately by default.
2. `ManualCommit.java` — multi-step transaction, commit on success.
3. `RollbackOnException.java` — multi-step transaction, rollback on failure. **The canonical pattern.**
4. `TransactionHelper.java` — wraps the boilerplate in a reusable `inTransaction(...)` helper.
5. `SavepointDemo.java` — partial rollback to a savepoint.
6. `ForgotCommit.java` — anti-example: forgetting to commit; data vanishes.

---

## Try this yourself

1. In `RollbackOnException.java`, comment out `conn.rollback()` in the catch. Re-run and observe the partial state.
2. In `TransactionHelper.java`, throw an exception inside the lambda. Confirm everything rolls back.
3. In `ForgotCommit.java`, add `conn.commit()` and re-run. Does the data survive?

---

## Self-check

1. What's the default value of autocommit on a fresh JDBC `Connection`, and what does it mean?
2. Write the four-step transaction pattern from memory (autocommit, work, commit/rollback, restore).
3. You forgot to `commit()` after disabling autocommit. What happens to your changes when the connection closes?
