# 16 — JDBC Batch Processing

When you need to write many rows — bulk imports, migrations, periodic syncs — running one `executeUpdate` per row is slow. Each call is a round trip to the database. JDBC's batch API lets you queue many operations and send them in a single round trip.

The speedup is typically 10–100×, depending on row count and network latency.

---

## The 3-line pattern

```java
try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO users (name, email) VALUES (?, ?)")) {
    for (User u : users) {
        ps.setString(1, u.name());
        ps.setString(2, u.email());
        ps.addBatch();                    // queue this row's parameters
    }
    int[] results = ps.executeBatch();    // send them all
}
```

That's it. `addBatch()` queues the current parameter values; `executeBatch()` sends them and returns an array of row counts (one per queued statement, in order).

---

## Why batching helps

Imagine you're inserting 10,000 rows.

**Without batching:**

```
10,000 × (send SQL + wait for ack) = 10,000 round trips
```

Each round trip takes ~1ms on a fast network. Total: ~10 seconds.

**With batching:**

```
1 × (send 10,000 rows + wait for ack) = 1 round trip
```

Total: ~50ms (mostly the time to send the data over the wire).

The savings come from network latency, not from less actual work. The database still inserts 10,000 rows — it just does so without your code waiting in between.

---

## Batching with a transaction

Without a transaction, each batch operation may auto-commit (depending on the driver). For real bulk writes you want them all-or-nothing:

```java
conn.setAutoCommit(false);
try (PreparedStatement ps = conn.prepareStatement(SQL)) {
    for (Row r : rows) {
        bindRow(ps, r);
        ps.addBatch();
    }
    ps.executeBatch();
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```

This is the production pattern: batched writes inside a transaction.

---

## Batch in chunks for very large data sets

Trying to batch 10 million rows in one `addBatch` loop is a memory disaster — the driver buffers them all. Chunk into reasonable groups:

```java
int CHUNK_SIZE = 1000;
int counter = 0;

for (Row r : rows) {
    bindRow(ps, r);
    ps.addBatch();
    counter++;
    if (counter % CHUNK_SIZE == 0) {
        ps.executeBatch();
        // optionally commit here too, so a failure doesn't lose everything
    }
}
ps.executeBatch();   // flush the last partial chunk
```

`CHUNK_SIZE = 500–5000` is a typical range. Tune based on row size.

---

## Partial failure: BatchUpdateException

If one row in the batch fails (e.g., duplicate primary key), the driver throws `BatchUpdateException`. What happened to the *other* rows depends on the database and the driver.

Possible behaviors:

- The whole batch is aborted; nothing is inserted.
- Earlier rows are inserted; later rows are skipped.
- Every row is attempted, with the failed ones marked as errors in the return array.

You read `BatchUpdateException.getUpdateCounts()` to find out which rows succeeded:

```java
try {
    ps.executeBatch();
} catch (BatchUpdateException e) {
    int[] counts = e.getUpdateCounts();
    for (int i = 0; i < counts.length; i++) {
        if (counts[i] == Statement.EXECUTE_FAILED) {
            System.out.println("Row " + i + " failed");
        }
    }
}
```

**Best practice:** combine batching with a transaction. If any row fails, rollback the whole transaction, fix the data, retry. Don't try to clean up partial state — it gets messy fast.

---

## What batching is NOT good for

- **Reading** — batching is only for writes. Reads (SELECTs) don't have a similar API; for big reads, you stream the ResultSet.
- **Heterogeneous statements** — you batch one prepared statement at a time, not a mix.
- **Real-time per-row responses** — if you need to know each row's generated ID one at a time, batching is awkward (you can do it but the API gets fiddly).

---

## When to use it

- Bulk imports (CSV → database)
- Periodic syncs (replicate from one system to another)
- Migrations (rewriting a million rows)
- Event/log ingestion (high write throughput)

For a normal API endpoint that creates one user at a time, you don't need batching. Use it when you have many writes happening together.

---

## Code examples

1. `BatchInsertBasic.java` — the 3-line pattern with 1000 rows.
2. `BatchVsLoopPerformance.java` — same 5000 inserts done one-at-a-time vs batched. Timed.
3. `BatchWithTransaction.java` — batched inserts wrapped in a transaction.
4. `PartialFailureHandling.java` — duplicate key in the middle of the batch. Read the `BatchUpdateException`.
5. `ChunkedBatching.java` — break a huge batch into 1000-row chunks.

---

## Try this yourself

1. In `BatchVsLoopPerformance.java`, increase the row count from 5000 to 50000. Does the speedup ratio grow?
2. In `BatchWithTransaction.java`, throw an exception in the middle of the loop. Verify nothing was inserted.
3. In `PartialFailureHandling.java`, change the duplicate row's position from the middle to the very last. Does the behavior change?

---

## Self-check

1. What's the underlying reason batching is faster than a `for` loop of `executeUpdate` calls?
2. Why combine batching with `setAutoCommit(false)` + `commit`?
3. You have 10 million rows to insert. Why is one giant `addBatch` loop a bad idea, and what's the fix?
