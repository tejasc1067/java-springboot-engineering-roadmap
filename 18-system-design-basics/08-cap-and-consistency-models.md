# CAP and Consistency Models

As soon as data lives on more than one machine (replicas in [topic 06](06-replication-and-read-replicas.md), shards in [topic 07](07-partitioning-and-sharding.md), caches in module 15), the machines can disagree, and the network between them can fail. **CAP** describes the one choice you can't avoid when the network splits. **Consistency models** are the vocabulary for what a reader is promised in the meantime. Together they explain behaviour you've already met: the stale replica, the CQRS read model that lags (module 13 topic 22), the cache that serves an old price.

The demo is a three-replica key-value store simulated in plain Java. The tests choose which replicas each client can reach, so a network partition is one line of code.

---

## The problem this solves

You run three replicas of a database, in two server rooms. The link between the rooms fails. Room A has two replicas, room B has one, and both rooms still have users. A user in room B wants to change their address. The system has exactly two options:

1. **Refuse**, because room B can't tell room A about the change. Users in B get errors, but nobody ever reads data that contradicts another replica.
2. **Accept** the write in room B. Users in B keep working, but rooms A and B now disagree, and someone has to sort it out when the link comes back.

There's no third option, and no amount of engineering removes the choice. That's CAP.

## How it works

### CAP, stated precisely

- **C**onsistency (here meaning *linearizability*): every read sees the most recent completed write, as if there were one copy.
- **A**vailability: every request to a working node gets a non-error answer.
- **P**artition tolerance: the system keeps operating while the network loses messages between nodes.

The common summary "pick 2 of 3" is misleading. Networks *do* partition, so you don't get to give up P. The real statement is: **during a partition, a system must choose between consistency and availability**. When there's no partition, it can have both.

**PACELC** adds what CAP leaves out: **if** there's a **P**artition, choose **A** or **C**; **e**lse, choose **L**atency or **C**onsistency. Even with a healthy network, keeping replicas in step means waiting for them. `LatencyTest.waitingForMoreReplicas_meansWaitingForSlowerOnes`: with replicas 5, 10, and 80 ms away, a write that waits for 1 replica takes 5 ms, for 2 replicas 10 ms, for all 3 80 ms. Synchronous replication in topic 06 was the same trade.

### Quorums: N, W, R

Many distributed databases let you tune this with three numbers:

- **N**: how many replicas hold each key.
- **W**: how many must acknowledge a write before it succeeds.
- **R**: how many are asked on a read; the newest value among them wins.

If **W + R > N**, every set of R replicas overlaps every set of W replicas in at least one replica, so every read sees the latest acknowledged write. `QuorumOverlapTest.wPlusRGreaterThanN_isExactlyWhenEveryReadSetMeetsEveryWriteSet` checks every combination for N = 3: overlap is guaranteed exactly when W + R > 3.

Concretely, with the writer reaching replicas in the order 0, 1, 2 and the reader starting from replica 2:

```java
// W=1, R=1: 1 + 1 is not > 3
store.write("price", "120", 2, WRITE_ORDER);       // acknowledged by replica 0 alone
store.read("price", READ_ORDER);                   // "100" - asked replica 2, which hasn't heard

// W=2, R=2: 2 + 2 > 3
store.write("price", "120", 2, WRITE_ORDER);       // replicas 0 and 1
store.read("price", READ_ORDER);                   // "120" - asked 2 and 1; 1 has it
```

(`w1r1_aReadCanMissTheLatestWrite`, `w2r2_theReadAlwaysReachesAReplicaWithTheLatestWrite`.) W = 2, R = 2 with N = 3 is a common "majority quorum" setting. Quorums alone don't make a system fully linearizable (concurrent writes and failed partial writes need more machinery), but they're the core idea.

### A partition: choosing C

Replicas 0 and 1 on one side, replica 2 alone on the other. With W = 2 and R = 2 (`PartitionTest.consistentChoice_theMinoritySideRefusesReadsAndWrites`):

```java
store.write("address", "9 Oak Ave", 2_000, MAJORITY_SIDE);    // fine: 2 of 3 reachable
store.write("address", "5 Elm Rd", 3_000, MINORITY_SIDE);     // UnavailableException
store.read("address", MINORITY_SIDE);                         // UnavailableException
```

Users who can only reach replica 2 get errors. Nobody reads a wrong address. This is a **CP** choice. Systems built on consensus (etcd, ZooKeeper, a relational database with synchronous failover) behave this way. So must a distributed lock (module 15 topic 09): a lock that two sides of a partition can both grant is worthless.

### A partition: choosing A, and what last-write-wins costs

With W = 1 and R = 1, both sides accept writes (`availableChoice_bothSidesAcceptWrites_andLastWriteWinsLosesOneOfThem`):

```java
// Real time 1,000 ms: Ann saves a new address on the majority side.
store.write("address", "9 Oak Ave", 1_000, MAJORITY_SIDE);
// Real time 2,000 ms: Ann corrects it from her phone, which reaches only replica 2.
// That server's clock is 1,500 ms slow, so it stamps the write 500.
store.write("address", "5 Elm Rd", 2_000 - 1_500, MINORITY_SIDE);

store.heal();   // every replica now holds "9 Oak Ave"
```

Both writes were acknowledged. During the split, each side answered with different data. When the network heals, the replicas must agree on one value. The simplest rule, **last-write-wins (LWW)**, keeps the larger timestamp, and timestamps come from server clocks, which drift. Ann's correction, the newer write in real time, is silently gone on every replica.

AP systems (Cassandra, DynamoDB-style stores, DNS, caches, and anything with asynchronous replication) accept this. The better ones offer more than LWW: version vectors that detect concurrent writes and keep both ("siblings") for the application to merge, or data types designed to merge automatically (CRDTs, such as counters and sets).

## Consistency models, from strongest to weakest

The guarantee a reader gets is a spectrum, not a yes/no. With examples you've met:

| Model | Promise | Broken when... |
|---|---|---|
| **Linearizable** (strong) | Every read sees the latest completed write, as if there were one copy | any read from an async replica or cache |
| **Read-your-writes** | You always see your own writes; others may lag | topic 06: the profile that showed the old name |
| **Monotonic reads** | Once you've seen a value, you never see an older one | topic 06: SHIPPED, PAID, SHIPPED |
| **Causal** | If B was caused by A (a reply to a comment), everyone who sees B also sees A | a reply appears before the comment it answers |
| **Eventual** | If writes stop, all replicas eventually agree. Nothing about *when*, or what you see before then | the read model in module 13 topic 22; module 13 topic 07's events |

Most real systems mix them: linearizable for the few operations that decide something (a payment, a lock, a unique username), read-your-writes for a user's own data, eventual for everything else (feeds, counters, search indexes, caches). Choosing per operation, and saying which one each screen gets, is the design work.

## When to use it / when not to

- **Choose CP** where acting on stale or conflicting data is worse than an error: money, inventory you can't oversell, locks, leader election, unique constraints.
- **Choose AP** where an answer that might be slightly old beats no answer: product pages, feeds, view counts, shopping carts (merge them later), caches (module 15 is AP by design: a cache serves its copy whether or not the database has changed).
- **Don't** describe a whole system as "CP" or "AP". Each operation makes its own choice, and the same database can be tuned per query (quorum settings, read from leader vs replica).

## Common pitfalls

- **"We picked CA."** There's no such option once data is on more than one machine and the network can fail. Decide what each operation does *during* a partition.
- **Last-write-wins with server clocks.** Clock skew silently discards newer writes. Use version numbers or vectors, or have one writer per key.
- **W + R ≤ N while assuming you read the latest value.** With W = 1 and R = 1 on N = 3, reads can miss acknowledged writes. Use majority quorums for reads that must be fresh.
- **Forgetting the latency side.** Waiting for every replica makes each write as slow as the farthest one. Wait for a majority, not all.

---

## Code examples

`CODE_EXAMPLES/08-cap-and-consistency-models/cap-demo/`. Plain Java, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/cap/QuorumStore.java`: N replicas, W and R, `heal()`, last-write-wins, `quorumLatency`.
2. `src/test/java/com/example/cap/QuorumOverlapTest.java`: the stale read with W = R = 1, the fresh read with W = R = 2, and every combination for N = 3.
3. `src/test/java/com/example/cap/PartitionTest.java`: the minority side refusing (CP), then both sides accepting and LWW losing a write (AP).
4. `src/test/java/com/example/cap/LatencyTest.java`: PACELC's "else": the cost of waiting for more replicas.

---

## Try this yourself

1. In `PartitionTest`, give the minority-side server a clock that is 1,500 ms *fast* instead of slow. Which address survives the heal now? Is the result "correct", or just lucky?
2. Change `QuorumStore` so a conflict on heal keeps both values instead of choosing one (store a list of siblings when timestamps come from different sides). What would the application have to do with them for a shopping cart? For an address?

## Self-check

1. State CAP in one sentence without the phrase "pick two". Why can't a distributed system choose to give up partition tolerance?
2. With N = 3, why does W = 2, R = 2 always read the latest acknowledged write while W = 1, R = 1 may not? What does W = 3 cost, even with no partition?
3. Name one operation in an online shop that should be CP and one that can be AP, and say what a user would see from each during a partition.
