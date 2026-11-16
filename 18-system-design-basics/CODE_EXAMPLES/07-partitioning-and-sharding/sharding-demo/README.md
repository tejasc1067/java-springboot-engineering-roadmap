# sharding-demo

Range, modulo, and consistent-hash sharding measured on 100,000 keys. Data is generated from a fixed
seed, so every number is the same on every run.

- `ModuloReshardTest`: `hash % N` spreads evenly, then moves 80% of keys going 4 -> 5 shards and 91%
  going 10 -> 11.
- `ConsistentHashingTest`: adding a 5th node moves 19% of keys, all to the new node; 1 virtual node per
  server gives a 49% spread, 100 give 4.6%.
- `HotSpotTest`: range sharding on a growing id sends every new write to the last shard; a celebrity key
  puts 47,207 of 100,000 writes on one shard; salting brings it to 22,387.
- `ScatterGatherTest`: a query by the shard key asks 1 shard of 8, any other query asks all 8.

Run (plain Java 21, no Docker):

```
mvn -q test
```
