# Replication and Read Replicas

**Replication** means keeping copies of the same database on several machines. One copy, the **leader**, accepts writes; the others, **followers** or **read replicas**, copy every change from it. Almost every production database runs this way, and almost every team meets the same surprise the first time they send reads to a replica: the copy is a little behind, and users notice.

The demo is a plain-Java simulation with a clock the tests move by hand, so "a little behind" is an exact number of milliseconds and every failure happens the same way on every run.

---

## The problem this solves

One database server is one machine. It can:

- **run out of read capacity.** A product catalogue is read thousands of times for every write. All those queries compete for one server's CPU and disk.
- **die.** A disk fails, a host is rebooted for patching. With one copy, the service is down until it's back, and if the disk is gone, so is the data.
- **be far away.** Users on another continent pay the round trip on every query.

Copies on other machines fix all three: replicas take read traffic, a replica can be **promoted** to leader when the leader dies (**failover**), and the data survives losing a disk. The price is that the copies aren't always identical.

## How it works

### Leader, followers, and the replication log

Every write goes to the leader. The leader appends it to a **replication log**, numbering each entry with a **log sequence number (LSN)** that only goes up. Each follower reads the log and applies the entries in order. In PostgreSQL this is the write-ahead log (WAL); in MySQL, the binlog.

```java
public long write(String key, String value) {
    LogEntry entry = new LogEntry(log.size() + 1, key, value, clock.now());   // the next LSN
    log.add(entry);
    data.put(key, value);
    ...
    return entry.lsn();
}
```

In the demo, a follower sees an entry `lagMs` after the leader wrote it:

```java
void catchUp() {
    for (Leader.LogEntry entry : leader.log()) {
        if (entry.lsn() <= appliedLsn) continue;
        if (entry.writtenAtMs() + lagMs > clock.now()) break;   // not arrived yet
        data.put(entry.key(), entry.value());
        appliedLsn = entry.lsn();
    }
}
```

That delay is **replica lag**. On a healthy system it's usually milliseconds. Under a heavy write burst, a long-running query on the replica, or a slow network, it grows to seconds or minutes. `ReplicaLagTest.aWriteReachesTheReplicaOnlyAfterTheLag`: with 500 ms of lag, the replica returns nothing at 0 ms and 499 ms, and the new value at 500 ms. `appliedLsn_saysHowFarBehindTheReplicaIs`: the leader is at LSN 3, the replica at LSN 1. Comparing LSNs is how you measure lag, and how the fixes below decide where a read may go.

### Synchronous vs asynchronous replication

The leader can acknowledge a write at two different moments:

| | Leader says "OK" when... | Write latency | If the leader dies |
|---|---|---|---|
| **Asynchronous** | it has written the change itself | fast: the leader's own write | writes the followers hadn't copied yet are **lost** |
| **Synchronous** | a follower has the change too | slower: plus the round trip to the follower | nothing acknowledged is lost |

`FailoverTest` shows both. With asynchronous replication and 1,000 ms of lag, the leader acknowledges three orders, crashes, and the promoted follower has only the first: `containsOnlyKeys("order:1")`. Two customers were told "order placed", and the orders no longer exist. With synchronous replication all three survive, and each write took 1,000 ms longer: 3,000 ms for three writes.

Real systems mostly sit in between. PostgreSQL's `synchronous_standby_names`, MySQL's semi-synchronous replication, and quorum settings in distributed databases wait for *one* follower (often in the same region) and copy to the rest asynchronously. The choice is the same trade-off as CAP's "else" branch in [topic 08](08-cap-and-consistency-models.md): consistency costs latency.

### Read scaling with replicas

Send writes to the leader and reads to replicas, and read capacity grows with each replica you add. Replicas don't help with write volume, though: every replica applies every write. When writes outgrow one leader, the answer is partitioning ([topic 07](07-partitioning-and-sharding.md)).

## Broken: the user doesn't see their own update

A user changes their name and saves. The write goes to the leader. The page reloads 50 ms later, and that read goes to a replica that is 500 ms behind. `ReadYourWritesTest.readsFromAReplica_theUserDoesNotSeeTheirOwnUpdate`:

```java
db.write("ann", "user:ann:name", "Ann Smith");   // saved, 200 OK
clock.advance(50);                              // the page reloads 50 ms later

db.read("ann", "user:ann:name");                // "Ann" - the old name
```

The user concludes the save didn't work and does it again, or files a bug. This is the guarantee called **read-your-writes** (or read-your-own-writes) being broken. It's the same symptom as CQRS projection lag in module 13 topic 22: a write succeeded, and the place the read comes from doesn't have it yet.

## Fixed: route reads after your own write

`ReadYourWritesReads` remembers the LSN of each user's last write. A read goes to a replica only if that replica has already applied it; otherwise it goes to the leader:

```java
public String read(String user, String key) {
    long mustHave = lastWriteLsnByUser.getOrDefault(user, 0L);
    for (Replica replica : replicas) {
        if (replica.appliedLsn() >= mustHave) return replica.read(key);
    }
    leaderReads++;
    return leader.read(key);
}
```

`readsAfterYourOwnWriteWaitForAReplicaThatHasIt_orGoToTheLeader` shows the three cases: Ann, who just wrote, gets "Ann Smith" from the leader; Bob, who wrote nothing, still reads from a replica (and may see the old name, which is fine for him); once the replicas catch up, Ann's reads go back to them. The leader only takes the reads that need it.

In a web application the LSN lives in the user's session or a cookie. Simpler variants of the same idea:

- **A time window.** For N seconds after a user writes, send that user's reads to the leader. Pick N above your normal lag, and monitor lag.
- **By feature.** Pages that show the user their own data ("my profile", "my orders") read from the leader; public pages read from replicas.
- **Wait for the version.** Some databases let a query say "only answer once you've applied position X" (for example MySQL's `WAIT_FOR_EXECUTED_GTID_SET`).

## Broken: the value goes back in time

Two replicas, one 100 ms behind and one 2,000 ms behind. An order moves from PAID to SHIPPED. Bob refreshes the page four times and each read goes to the next replica. `MonotonicReadsTest.differentReplicasPerRead_theValueGoesBackInTime`:

```text
SHIPPED, PAID, SHIPPED, PAID
```

Seeing old data is one thing. Seeing new data and *then* old data is worse: the order looks like it was un-shipped. The guarantee broken here is **monotonic reads**: once you've seen a value, you never see an older one.

## Fixed: one replica per user

`StickyReads` always sends a user to the same replica (`hash(user) % replicas`). `stickyReplicaPerUser_mayBeStale_butNeverGoesBackwards`: Bob happens to be on the slow replica, so he sees `PAID, PAID, PAID, SHIPPED` as time passes. Stale for a while, but always moving forward. If his replica fails, he moves to another one and may see one step back, so combine it with the LSN check when that matters.

## Spring: routing reads to replicas

The usual Spring setup routes by transaction type: methods marked `@Transactional(readOnly = true)` go to a replica, everything else to the leader. It's built from an `AbstractRoutingDataSource` whose `determineCurrentLookupKey()` returns `"replica"` when `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` is true. That routing data source is normally wrapped in a `LazyConnectionDataSourceProxy`, so the real connection is fetched when the first statement runs, after the transaction has been marked read-only, rather than when the transaction begins.

Everything in this topic applies to that setup: a `readOnly` method called right after a write reads from a replica that may not have the write. Reads that must see the user's own change belong in a read-write transaction, or in one that routes to the leader on purpose.

## When to use it / when not to

- **Use replicas** for read-heavy workloads (catalogues, feeds, reports) and for failover. Almost every production database should have at least one replica for durability alone.
- **Don't send reads to replicas** when the read decides something: a balance before a withdrawal, stock before a reservation, a permission check right after it was revoked. Read those from the leader, inside the transaction that acts on them (module 10's transactions).
- **Asynchronous replication** is the default almost everywhere because it's fast. Know how much it can lose: everything newer than the follower's last applied LSN.

## Common pitfalls

- **Reading your own write from a replica.** Route that user's reads to the leader for a window, or check the replica's LSN against the user's last write.
- **Round-robin reads across replicas with different lag.** Data goes back in time on refresh. Pin each user to one replica.
- **Assuming failover loses nothing.** With asynchronous replication, acknowledged writes newer than the follower's position are gone. Use synchronous or semi-synchronous replication for data you can't lose.
- **Not monitoring lag.** A replica minutes behind looks healthy until a user notices. Alert on replication lag like on any other SLI (module 17 topic 09).

---

## Code examples

`CODE_EXAMPLES/06-replication-and-read-replicas/replication-demo/`. Plain Java, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/replication/SimClock.java`: the hand-moved clock.
2. `src/main/java/com/example/replication/Leader.java`: writes, the replication log, LSNs, optional synchronous followers.
3. `src/main/java/com/example/replication/Replica.java`: applies the log after `lagMs`; `appliedLsn()`, `promote()`.
4. `src/test/java/com/example/replication/ReplicaLagTest.java`: lag in milliseconds and in LSNs.
5. `src/main/java/com/example/replication/RoundRobinReads.java`: the naive router.
6. `src/main/java/com/example/replication/ReadYourWritesReads.java`: the LSN-checking router.
7. `src/test/java/com/example/replication/ReadYourWritesTest.java`: the stale profile, then the fix.
8. `src/main/java/com/example/replication/StickyReads.java`: one replica per user.
9. `src/test/java/com/example/replication/MonotonicReadsTest.java`: the order that goes back in time, then the fix.
10. `src/test/java/com/example/replication/FailoverTest.java`: lost writes with asynchronous replication; the latency of synchronous replication.

---

## Try this yourself

1. Add the time-window fix: a router that sends a user's reads to the leader for 1,000 ms after their write and to replicas otherwise. Write a test showing it works with 500 ms of lag and fails with 1,500 ms. What does that tell you about choosing the window?
2. In `FailoverTest`, add a second follower with 100 ms of lag and promote whichever follower has the highest `appliedLsn()`. How many writes are lost now? This is what real failover tooling does.

## Self-check

1. A user updates their profile, the save succeeds, and the reloaded page shows the old value. Explain exactly why, and give two different fixes.
2. Why can round-robin reads across replicas make an order look un-shipped, and why does pinning a user to one replica prevent it?
3. The leader acknowledged a write and then crashed. Under what replication setting is that write lost after failover, and what does avoiding the loss cost on every write?
