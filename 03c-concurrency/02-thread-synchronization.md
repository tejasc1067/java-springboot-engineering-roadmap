# 02 — Thread Synchronization

When two threads touch the same mutable state, you can lose data, see impossible values, or just silently get the wrong answer. Java's primary tool to prevent this is the `synchronized` keyword, which gives a thread exclusive access to a critical section. This topic covers what a race condition really is, how `synchronized` fixes it, the difference between locking a method and a block, and the costs.

Topic 14 covers the related visibility problem and `volatile`. Topic 17 covers the modern `Lock` API (`ReentrantLock`, `ReadWriteLock`) when `synchronized` isn't enough.

---

## The problem this solves

Two threads both run `balance = balance + 100`. That single line is actually three CPU instructions: read `balance`, add 100, write `balance`. If thread A reads, then B reads (same value), both add 100, both write — one of the deposits vanishes. The variable `balance` ends up 100 higher instead of 200.

This is a **race condition**: the answer depends on which thread happens to win the scheduling race. It's not a rare bug — under load it's nearly constant.

---

## Vulnerable code

```java
class BankAccount {
    private long balance;
    public void deposit(long amount) { balance += amount; }   // NOT atomic
    public long getBalance() { return balance; }
}
```

Run 8 threads each calling `deposit(1)` a million times. Expected balance: 8,000,000. Actual: usually 4–7 million. Money is being *lost* because of the read-modify-write race.

`balance += amount` compiles to roughly:

```
GETFIELD balance     // read current value
IADD                 // add amount
PUTFIELD balance     // write back
```

Any other thread can read between the read and the write. They both then write back based on stale data.

---

## Fix: `synchronized`

`synchronized` makes a section of code execute under a lock. While one thread holds the lock, every other thread asking for it waits.

```java
class BankAccount {
    private long balance;
    public synchronized void deposit(long amount) { balance += amount; }
    public synchronized long getBalance() { return balance; }
}
```

Now `deposit` and `getBalance` cannot interleave. Every increment completes before the next thread starts. The final balance is exactly 8,000,000.

Java's `synchronized` is **reentrant**: the thread that already holds the lock can re-enter the same lock without deadlocking itself. (A `synchronized` method calling another `synchronized` method on the same object is fine.)

---

## Synchronized method vs synchronized block

Every Java object has a single intrinsic lock (also called its *monitor*). `synchronized` always locks one specific monitor.

**Synchronized method** — locks `this` (for instance methods) or the `Class` object (for `static` methods).

```java
public synchronized void deposit(long n) { ... }
// equivalent to:
public void deposit(long n) {
    synchronized (this) { ... }
}
```

**Synchronized block** — locks any object you name.

```java
private final Object lock = new Object();
public void deposit(long n) {
    synchronized (lock) { balance += n; }
}
```

Why blocks are usually better:

- **Smaller critical section.** Only the lines that need the lock are inside it. The rest runs concurrently.
- **Private lock object.** Outside code can't grab your lock and deadlock you. A `synchronized` method exposes `this` as the lock — anyone holding a reference can lock it.

---

## Instance lock vs class lock

```java
class Counter {
    private int x;
    private static int y;

    public synchronized void incX() { x++; }            // locks `this` (one lock per instance)
    public static synchronized void incY() { y++; }     // locks `Counter.class` (one lock for ALL instances)
}
```

If you have two `Counter` objects, two threads can call `incX()` on different instances at the same time — they hold different locks. But only one thread at a time can call `incY()`, ever — there's one `Counter.class` for the whole JVM.

A classic bug: a `synchronized` instance method "protecting" a `static` field. Two instances → two locks → race.

---

## Visibility (preview of topic 03)

Synchronization doesn't only provide mutual exclusion — it also flushes memory. When thread A releases a lock and thread B acquires the same lock, every write A made inside the block is visible to B. Without that, B might keep reading a cached old value forever.

Detail lives in topic 03 (`volatile`, memory model).

---

## Cost of synchronization

`synchronized` isn't free. The first cost is **contention** — threads wait for the lock, doing nothing useful. The second is **memory barriers** — the CPU flushes caches when entering and leaving the block, which is slow. The third is **scalability ceiling** — even if you add 16 cores, one lock means at most one thread runs the critical section at a time.

Rules of thumb:

- Lock the *smallest* possible region.
- Lock only the *actual* shared state.
- If reads vastly outnumber writes, look at `ReadWriteLock` or immutability (topic 03/06).
- If the critical section is just a counter, use `AtomicInteger` (topic 06) — no lock at all.

---

## Common pitfalls

- **Locking on `this` while exposing it.** External code can do `synchronized (account) { /* hold lock forever */ }` and freeze your class. Use a private final `Object lock`.
- **Locking on a `String` or `Integer`.** These can be interned/cached — different objects may share the same lock by accident. Use a dedicated lock object.
- **Synchronizing the wrong scope.** `synchronized` on the static method but the field is non-static (or vice versa). The lock and the data don't match → race remains.
- **Holding a lock across slow operations.** `synchronized (lock) { networkCall(); }` blocks every other thread until the network call returns. Reduce the critical section.
- **Double-checked locking without `volatile` (Java < 1.5 idiom).** On modern Java the field must be `volatile` for the pattern to be safe; without it, partially-constructed objects can leak.

---

## Code examples

1. `RaceConditionBroken.java` paired with `SynchronizedFixed.java` — same workload, broken vs fixed.
2. `SynchronizedMethodVsBlock.java` — method-level lock vs narrow block lock.
3. `PrivateLockObject.java` — why you should not lock on `this`.
4. `InstanceVsClassLock.java` — instance lock protecting a static field is a bug.
5. `ReentrantSynchronized.java` — a `synchronized` method calling another doesn't deadlock itself.

---

## Try this yourself

1. In `RaceConditionBroken.java`, change the thread count and per-thread increments. Confirm the loss gets worse with more contention.
2. In `SynchronizedMethodVsBlock.java`, time both versions. The block version should be faster because the lock window is smaller.
3. In `PrivateLockObject.java`, comment out the private-lock version and rerun. Notice how external code is able to grab `this`.

---

## Self-check

1. Why is `balance += 1` not atomic, even though it looks like one statement?
2. You have a `synchronized` instance method that increments a `static` counter. Two threads call it on two different objects. Is the counter safe? Why or why not?
3. When would you choose a synchronized block over a synchronized method, and what's the concrete benefit?
