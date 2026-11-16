# replication-demo

A leader, followers that apply its log after a fixed lag, and three ways of routing reads. A simulated
clock makes every lag exact; nothing sleeps.

- `ReplicaLagTest`: a write reaches the replica exactly 500 ms later; lag measured in LSNs.
- `ReadYourWritesTest`: round-robin reads show a user their old name 50 ms after saving; the LSN-checking
  router sends only that user's read to the leader.
- `MonotonicReadsTest`: two replicas with different lag make an order go SHIPPED, PAID, SHIPPED, PAID;
  a sticky replica per user never goes backwards.
- `FailoverTest`: asynchronous replication loses two acknowledged orders on failover; synchronous keeps
  all three and costs 1,000 ms per write.

Run (plain Java 21, no Docker):

```
mvn -q test
```
