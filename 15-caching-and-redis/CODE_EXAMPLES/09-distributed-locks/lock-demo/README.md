# lock-demo

Redis locks from naive to careful, against a real Redis, plus Redisson (`org.redisson:redisson:4.8.0`).

- `SynchronizedAcrossInstancesTest` — two "instances" sell the last lamp twice under `synchronized`; with a Redis lock
  the second sale is refused.
- `NaiveLockTest` — a crash between `SETNX` and `EXPIRE` leaves a lock with no TTL; `SET NX PX` doesn't; releasing
  with `DEL` after the lease expired deletes the next holder's lock; the check-and-delete Lua script doesn't.
- `FencingTokenTest` — a holder paused past its lease overwrites the new holder's work; with fencing tokens the storage
  rejects the stale write.
- `RedissonLockTest` — the watchdog keeps a lock alive past its lease, stops when the holder dies, is off with an
  explicit lease time; only the owner can unlock.

Run (Docker running):

```
mvn -q test
```

The suite takes about 7 seconds. `RedissonLockTest` sleeps 2.5 seconds once: proving the watchdog renews the lease
needs real time to pass.
