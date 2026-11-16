# cap-demo

A three-replica key-value store with tunable write (W) and read (R) quorums. Each call says which
replicas the client can reach, so a network partition is just a shorter list.

- `QuorumOverlapTest`: W=1, R=1 reads an old price; W=2, R=2 reads the new one; every W/R combination
  for N=3 overlaps exactly when W + R > 3.
- `PartitionTest`: with majority quorums the minority side refuses reads and writes (CP); with W=1 both
  sides accept writes and last-write-wins, with a slow clock, discards the newer one (AP).
- `LatencyTest`: waiting for 1, 2, or 3 replicas costs 5, 10, or 80 ms (PACELC's "else").

Run (plain Java 21, no Docker):

```
mvn -q test
```
