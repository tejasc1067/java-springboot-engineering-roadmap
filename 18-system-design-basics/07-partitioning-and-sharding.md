# Partitioning and Sharding

Replication ([topic 06](06-replication-and-read-replicas.md)) gives you more copies of the same data. It doesn't help when the data itself, or the stream of writes, is too big for one machine: every replica still holds everything and applies every write. **Partitioning** splits the data into pieces and puts each piece on a different machine. When the pieces live on separate database servers it's usually called **sharding**, and each piece is a **shard**.

The two decisions that matter are *which key* splits the data and *how* keys map to shards. Get either wrong and you get one overloaded shard, queries that must ask every shard, or a resize that moves nearly all your data. The demo measures each of these on 100,000 keys.

---

## The problem this solves

An orders table grows by a few million rows a day. Eventually it doesn't fit on one server's disk, its indexes don't fit in memory, and the leader can't keep up with the writes. Bigger hardware buys time (topic 01's vertical scaling), but it has a ceiling and gets expensive fast.

Splitting the table across, say, 8 servers by customer gives each server an eighth of the data and an eighth of the writes. The cost is that the application, or a routing layer, must know which server holds which customer.

## How it works

### Partition by key

Every row gets a **partition key** (shard key), and a function maps the key to a shard. Two families:

| | How | Good at | Bad at |
|---|---|---|---|
| **Range** | `id 0–999,999` → shard 0, `1,000,000–1,999,999` → shard 1, ... | range scans ("orders from March"), keeping neighbours together | keys that grow over time: all new writes hit the last range |
| **Hash** | `hash(key) % N`, or a hash ring | spreading keys evenly | range queries: neighbours are scattered |

The demo's hash is the first 8 bytes of MD5, which is stable across machines and spreads well. Never use something like `Object.hashCode()` for routing data: it isn't guaranteed to be the same across JVMs or versions.

### Broken: range sharding on a growing id

`HotSpotTest.rangeShardingOnAGrowingId_sendsEveryNewWriteToTheLastShard`: four shards of one million ids each, and today's 100,000 new orders have ids 3,000,000 to 3,099,999. All 100,000 writes go to shard 3; the other three shards sit idle. Timestamps and auto-increment ids do this. Hashing the same ids (`hashingTheSameIds_spreadsTheNewWrites`) gives each shard between 24,000 and 26,000.

### Broken: `hash % N` when N changes

`ModuloSharding.shardFor(key, n)` is `hash(key) % n`. It spreads keys evenly (`hashModN_spreadsKeysEvenly`: under 1% difference between shards). Then the data grows and you add a shard. `ModuloReshardTest.addingOneShard_movesAlmostEveryKey`:

```text
4  -> 5  shards: 80,021 of 100,000 keys change shard  (80%,  expected 4/5)
10 -> 11 shards: 90,908 of 100,000 keys change shard  (91%,  expected 10/11)
```

A key stays put only if `hash % N` and `hash % (N+1)` happen to agree, which is about 1 time in N+1. So adding a shard means copying almost the whole dataset across the network, and the more shards you have, the worse it gets. Kafka has the same problem with partition keys (module 16 topic 05): adding partitions moved 507 of 1,000 keys there.

### Fixed: consistent hashing

Put the shards on a circle of hash values, and put each key on the same circle. A key belongs to the first shard clockwise from it:

```java
public String nodeFor(String key) {
    Map.Entry<Long, String> owner = ring.ceilingEntry(Hashing.hash(key));
    return owner != null ? owner.getValue() : ring.firstEntry().getValue();   // wrap around the circle
}
```

Adding a node puts it somewhere on the circle; it takes over only the keys between it and its neighbour. `ConsistentHashingTest.addingANode_movesAboutOneFifth_andOnlyToTheNewNode`: going from 4 to 5 nodes moves 19,073 keys (19%, ideal 1/5), and **none** move between the old nodes. Removing a node (`removingANode_movesOnlyThatNodesKeys`) moves only that node's keys.

### Virtual nodes

With one point per node, the arcs between points are random lengths, so some nodes own much more of the circle than others. `oneVirtualNodePerServer_isLopsided_manyVirtualNodesEvenItOut`:

| Points per node | Busiest node | Quietest node | Standard deviation |
|---|---|---|---|
| 1 | 40,773 keys | 6,130 keys | 49% of the average |
| 100 | under 27,000 | over 23,000 | 4.6% of the average |

Each node is placed at many points (`node-0#0`, `node-0#1`, ...), so its share is the sum of many small arcs, which averages out. Virtual nodes also let a bigger machine take more points. Cassandra, DynamoDB-style stores, and many caches and load balancers (topic 04) use consistent hashing with virtual nodes. Redis Cluster uses a related scheme: 16,384 fixed **hash slots**, assigned to nodes, so resharding moves whole slots.

## Choosing the shard key

The key decides which queries are cheap:

- **A query that includes the shard key** goes to one shard. `ScatterGatherTest.aQueryByTheShardKey_asksOneShard`: orders sharded by customer, "orders of customer-42" asks 1 shard of 8.
- **A query that doesn't** must ask every shard and merge the answers: **scatter-gather**. `aQueryByAnythingElse_asksEveryShard`: "orders of product-3" asks all 8. It's as slow as the slowest shard, and it costs every shard work. A secondary index on product, kept per shard (a *local* index), doesn't change that; a *global* index sharded by product does, but must be updated on every write, usually asynchronously.
- **Joins and transactions across shards** become distributed problems (module 13 topic 18's sagas). Keep data that's read and changed together on the same shard: an order and its lines share the customer id as shard key.

So pick the key from the most important queries, not from the data model. For a multi-tenant SaaS, the tenant id is often the natural choice; for a social app, the user id.

### Broken: the hot key

Even a perfect hash can't split a single key. `HotSpotTest.aCelebrityKey_overloadsOneShard_whateverTheHashing`: 40% of 100,000 posts are by one celebrity. Sharded by author over 8 shards, one shard takes 47,207 writes and the other seven about 7,500 each.

The usual fix is **salting** the known hot keys: write the celebrity's posts under `celebrity#0` to `celebrity#7`, spreading them over several shards (`saltingTheHotKey_spreadsItsWrites_butItsReadsMustAskEverySalt`). The busiest shard drops to 22,387, not 12,500, because with 8 salts on 8 shards some salts land on the same shard; more salts spread it further. The price: reading the celebrity's posts now takes 8 lookups instead of 1. Only salt keys you know are hot.

## When to use it / when not to

- **Don't shard until you must.** A single well-indexed PostgreSQL server with read replicas handles far more than most services need. Sharding makes every cross-entity query, join, transaction, migration, and backup harder.
- **Before sharding:** archive old data, add replicas for reads, cache (module 15), split by feature into separate databases (module 13's database per service).
- **When you shard,** prefer hashing with many fixed partitions (more partitions than servers, moved as whole units) or consistent hashing, so you can grow without rewriting everything.

## Common pitfalls

- **`hash % N` with N = number of servers.** Adding a server moves ~N/(N+1) of the data. Use consistent hashing or fixed slots.
- **A shard key that grows over time** (timestamp, auto-increment id) with range sharding. All writes hit one shard. Hash it, or prefix it.
- **A shard key that doesn't match the main queries.** Every request becomes scatter-gather. Choose the key from the access patterns.
- **Ignoring hot keys.** One celebrity, one big tenant, one viral product overloads its shard regardless of hashing. Measure per-key load, and salt or isolate the hot ones.

---

## Code examples

`CODE_EXAMPLES/07-partitioning-and-sharding/sharding-demo/`. Plain Java, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/sharding/Hashing.java`: a stable 64-bit hash.
2. `src/main/java/com/example/sharding/ModuloSharding.java`: `hash % N`.
3. `src/test/java/com/example/sharding/ModuloReshardTest.java`: even spread, then 80% and 91% of keys moving.
4. `src/main/java/com/example/sharding/ConsistentHashRing.java`: the ring with virtual nodes.
5. `src/test/java/com/example/sharding/ConsistentHashingTest.java`: 19% moved, only to the new node; balance with 1 and 100 virtual nodes.
6. `src/main/java/com/example/sharding/RangeSharding.java`: ranges of ids.
7. `src/test/java/com/example/sharding/HotSpotTest.java`: the last range taking every new write; the celebrity key; salting.
8. `src/main/java/com/example/sharding/OrderShards.java` and `src/test/java/com/example/sharding/ScatterGatherTest.java`: one shard vs every shard.

---

## Try this yourself

1. In `ConsistentHashingTest`, measure how many keys move going from 4 to 5 nodes with 1 virtual node instead of 100. Why is it so different from 20%?
2. Change the salting test to use 32 salts. What happens to the busiest shard, and how many lookups does reading the celebrity's posts take now?

## Self-check

1. You have 10 shards using `hash(key) % 10` and add one more. Roughly what fraction of the data has to move, and why does consistent hashing move so much less?
2. Orders are sharded by customer id. Which of these queries ask one shard and which ask all of them: "orders of customer 42", "today's orders for product 7", "order 9001 by id"? What would you change to make the third one cheap?
3. One tenant generates 40% of all writes. Why doesn't a better hash function help, and what does salting cost you?
