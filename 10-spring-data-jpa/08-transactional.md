# `@Transactional` — propagation, rollback rules, where to put it

Every JPA write happens inside a transaction. So far, the repository's own methods have been giving you one (`save`, `delete`, `findAll` — all wrapped in their own short transactions). For anything that touches the database more than once and needs to commit-or-rollback as a unit, you need to *own* the transaction at the service layer. That's what `@Transactional` is for. This topic covers what it does, where to put it, the four most-asked-about behaviors, and the two pitfalls that have humbled every JPA team.

---

## The problem this solves

A bank transfer is two operations: debit one account, credit another. If the debit succeeds and the credit fails, you have to **undo** the debit — or you've just deleted money. The unit "both succeed or both undo" is a transaction.

JPA inherits that idea from JDBC. `EntityManager.persist`, `merge`, `remove`, and `flush` all require an open transaction; outside one, they throw `TransactionRequiredException`. Hibernate's persistence context, dirty checking, and write-ordering are all scoped to the transaction.

You could do it by hand:

```java
EntityTransaction tx = em.getTransaction();
try {
    tx.begin();
    // ... work ...
    tx.commit();
} catch (RuntimeException e) {
    if (tx.isActive()) tx.rollback();
    throw e;
}
```

That's tedious and easy to get wrong. `@Transactional` is the declarative shortcut.

---

## How it works

`@Transactional` is a Spring AOP annotation. At startup, Spring wraps every bean that has `@Transactional` (on the class or on individual methods) in a proxy. When a method is called *through that proxy*, the proxy:

1. Starts (or joins) a transaction before the method body runs.
2. Lets the method body run.
3. On normal return, commits.
4. On a `RuntimeException` (or `Error`), rolls back.
5. On a checked `Exception`, **commits anyway** — unless `rollbackFor` says otherwise.

Look at the demo `BookService.java`:

```java
@Service
public class BookService {

    @Transactional
    public void renameBook(Long id, String newTitle) {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);     // no save() call — dirty checking does it
    }
}
```

The test `dirtyChecking_persistsChangeWithoutExplicitSave`:

```java
service.renameBook(bookId, "Renamed");
Book reloaded = repository.findById(bookId).orElseThrow();
assertThat(reloaded.getTitle()).isEqualTo("Renamed");      // committed
```

Because the whole method ran inside one transaction, the `Book` returned by `findById` was a *managed* entity, dirty checking noticed the title change, and Hibernate emitted an UPDATE at commit. No explicit `save`.

---

## Where to put it — the service layer rule

A quick map of where `@Transactional` belongs:

| Layer | Should you put `@Transactional` here? |
|---|---|
| **Controller** | No. Controllers translate HTTP to method calls. Putting transactions on the controller couples HTTP shape to DB shape. |
| **Service** | **Yes.** This is the unit of work — one user-facing operation, one transaction. |
| **Repository** | Usually no — Spring Data JPA's `SimpleJpaRepository` already annotates standard methods (`@Transactional` on save/delete, `@Transactional(readOnly = true)` on reads). Custom repository methods inherit that. |
| **Entity** | No. Entities are data. |
| **Static helper / util** | No. Static methods don't go through the Spring proxy. |

The rule that works in 95% of cases: **`@Transactional` on the service method that represents one user-facing operation**. Anything that operation needs (multiple repository calls, multiple entity updates, derived calculations) lives inside that one transaction.

---

## The rollback rule that bites everyone

By default, `@Transactional` rolls back **only on `RuntimeException` and `Error`**. It **commits** when the method throws a checked `Exception`.

This sounds wrong. It is, in fact, what 90% of teams have to learn the hard way. The rationale is historical (EJB 1 set this convention in 1998), and Spring kept the behavior so existing code wouldn't break on upgrade.

The demo proves it. With this service method:

```java
@Transactional
public void renameAndThrowChecked(Long id, String newTitle) throws OutOfPagesException {
    Book book = repository.findById(id).orElseThrow();
    book.setTitle(newTitle);
    throw new OutOfPagesException("checked");
}
```

…and `OutOfPagesException extends Exception` (not `RuntimeException`):

```java
assertThatThrownBy(() -> service.renameAndThrowChecked(bookId, "Renamed"))
        .isInstanceOf(OutOfPagesException.class);

Book reloaded = repository.findById(bookId).orElseThrow();
assertThat(reloaded.getTitle()).isEqualTo("Renamed");   // committed despite the exception
```

The title changed. The exception was thrown. **No rollback happened.** That's the gotcha.

The fix is one annotation parameter:

```java
@Transactional(rollbackFor = OutOfPagesException.class)
public void renameAndThrowChecked_withRollbackFor(Long id, String newTitle) throws OutOfPagesException {
    // ...
}
```

Now the title rolls back. You can also write `rollbackFor = Exception.class` to roll back on any exception, which is what most teams set as their project-wide default.

Compare with `RuntimeException`:

```java
@Transactional
public void renameAndThrowRuntime(...) {
    book.setTitle(newTitle);
    throw new RuntimeException("boom");   // RuntimeException → rolled back automatically
}
```

The test confirms `getTitle()` is back to "Original Title" — runtime exceptions roll back by default.

**Project-level convention to adopt:** declare your domain exceptions as unchecked (extends `RuntimeException`), or set `@Transactional(rollbackFor = Exception.class)` as the default on your service base class. Either approach makes the rollback behavior match what you'd intuitively expect.

---

## `readOnly = true` — the optimization with teeth

```java
@Transactional(readOnly = true)
public Book findById(Long id) {
    return repository.findById(id).orElseThrow();
}
```

`readOnly = true` does three things:

1. **Hints the JDBC driver and connection pool** that this transaction won't write. Some drivers / database setups (read-replica routing, e.g.) use this to send the query to a replica.
2. **Disables Hibernate's dirty checking for this session.** No snapshot of loaded entities is kept; entity modifications are silently ignored at flush time.
3. **Prevents accidental writes from succeeding.**

The third point is the operational guard. The demo's `renameInReadOnly`:

```java
@Transactional(readOnly = true)
public void renameInReadOnly(Long id, String newTitle) {
    Book book = repository.findById(id).orElseThrow();
    book.setTitle(newTitle);
}
```

The test reloads and finds the title unchanged. The `setTitle` did nothing — dirty checking was off, so Hibernate didn't emit the UPDATE. **No exception was raised.** This is a silent ignore, which is its own kind of subtle. Use `readOnly` deliberately on methods that are truly reads.

A common pattern: annotate the entire service *class* with `@Transactional(readOnly = true)` as a default, then mark individual write methods with `@Transactional` (no `readOnly`). That way "read" is the default and write methods stand out:

```java
@Service
@Transactional(readOnly = true)
public class BookService {

    public Book findById(Long id) { ... }              // inherits readOnly
    public List<Book> findRecent(int year) { ... }      // inherits readOnly

    @Transactional
    public void renameBook(Long id, String newTitle) { ... }   // overrides to writable
}
```

---

## Propagation — what happens when a `@Transactional` method calls another

When a `@Transactional` method calls another `@Transactional` method, what happens to the second transaction? `propagation` answers that.

| Propagation | Meaning | When to use |
|---|---|---|
| `REQUIRED` (default) | Join the existing transaction. If none, start a new one. | 95% of cases. |
| `REQUIRES_NEW` | Suspend the existing transaction, run in a brand-new one, then resume the outer. | Audit logging that must commit even if the outer rolls back. |
| `SUPPORTS` | Use a transaction if one exists; otherwise run with no transaction. | Rare. Methods that can work either way. |
| `MANDATORY` | Use the existing transaction. Throw if none. | Methods that must be called from within an outer transaction. |
| `NEVER` | Throw if a transaction exists. | Methods that must not be in a transaction. |
| `NOT_SUPPORTED` | Suspend the existing transaction, run with no transaction. | Long-running reports that don't need a transaction. |
| `NESTED` | A nested savepoint inside the outer transaction. | Rare; only some drivers support it. |

`REQUIRED` is the default and almost always the right choice. The most common deliberate override is `REQUIRES_NEW`, used when you have *audit* writes that must persist even if the surrounding operation rolls back:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordAudit(AuditEvent event) {
    auditRepository.save(event);
}
```

Now an outer `@Transactional` method can call `recordAudit(...)`, and even if the outer rolls back, the audit row is committed in its own transaction.

---

## The self-invocation pitfall

This one is famous. `@Transactional` only works when the method is called **through the Spring proxy**. Calling another method on the *same bean* via `this.` bypasses the proxy — and the inner method's `@Transactional` is silently ignored.

The demo:

```java
public void renameViaSelfInvocation(Long id, String newTitle) {
    this.renameBook(id, newTitle);   // bypasses the proxy
}

@Transactional
public void renameBook(Long id, String newTitle) {
    Book book = repository.findById(id).orElseThrow();
    book.setTitle(newTitle);
}
```

`renameViaSelfInvocation` is NOT `@Transactional`. Inside, it calls `this.renameBook(...)`. The call bypasses the proxy — so `renameBook` runs as if its `@Transactional` weren't there. Without a transaction:

- `findById` opens its own short transaction (because `SimpleJpaRepository` annotates the method).
- That transaction closes when `findById` returns.
- The `Book` is now **detached** — no longer managed.
- `setTitle` modifies a detached object. No flush, no UPDATE.
- The method returns and nothing happens.

The test confirms: `getTitle()` is "Original Title", not "Renamed". The change vanished silently.

Three fixes:

1. **Restructure**: put the second method on a different bean (then `this.` becomes `otherBean.`, which goes through the proxy).
2. **Inject self**: `@Autowired private BookService self;` then call `self.renameBook(...)`. Ugly but works.
3. **Use `AopContext.currentProxy()`**: `((BookService) AopContext.currentProxy()).renameBook(...)`. Works but tightly coupled to AOP internals.

The least-bad answer is usually (1) — if you find yourself calling `this.someTransactionalMethod()` from another method on the same class, separate them into two beans.

---

## Common pitfalls

- **Putting `@Transactional` on a `private` method.** Spring's AOP proxies cannot intercept private methods. The annotation is ignored. Fix: make the method `public`, or split it into a separate bean.
- **Catching `RuntimeException` inside a `@Transactional` method and not re-throwing.** The transaction commits even though something went wrong (Spring sees no exception, so it commits). Fix: re-throw, or mark the transaction for rollback manually with `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.
- **Assuming `@Transactional` rolls back on `Exception`.** It doesn't — only `RuntimeException` and `Error`. Set `rollbackFor = Exception.class` or use unchecked exceptions for domain errors.
- **Self-invocation.** Calling `this.x()` where `x` is `@Transactional` does nothing. Restructure into separate beans.
- **Long-running `@Transactional` methods.** Transactions hold database connections. A method that does 30 seconds of work blocks one connection in the pool for 30 seconds. For long jobs, split into multiple shorter transactions, or use `Propagation.NOT_SUPPORTED` for the slow parts.
- **`@Transactional(readOnly = true)` on a method that writes.** No exception — silent ignore. The write goes nowhere. Check the annotation when "the save didn't happen but no error appeared."
- **Putting `@Transactional` on the controller.** Couples web concerns with persistence and opens the transaction earlier than necessary (often before request validation). Put it on the service.

---

## Try this yourself

1. Remove `@Transactional` from `renameBook` and re-run the dirty-checking test. It fails: without a transaction, `setTitle` on a detached entity has no effect.
2. Replace `OutOfPagesException extends Exception` with `extends RuntimeException`. Re-run `checkedException_doesNotRollBack_byDefault` — it now fails because the rollback *does* happen. Convert it back to checked and add `rollbackFor = OutOfPagesException.class`.
3. Add a `service.recordAudit(...)` method with `propagation = REQUIRES_NEW`. Call it from a method whose outer transaction rolls back. Confirm the audit row persists.
4. Change one of the service methods to `private`. Re-run — the `@Transactional` becomes ineffective and the test fails. Read the resulting behavior carefully (no exception, just no transaction).

## Self-check

1. Why does `@Transactional` on a `private` method not work?
2. By default, on what kind of exception does `@Transactional` roll back? On what kind does it commit? How do you change that?
3. What does `@Transactional(readOnly = true)` actually do when Hibernate sees a dirty entity?
4. You write `service.outer()` which calls `this.inner()`, and `inner()` is `@Transactional`. Does `inner()` open a transaction? Why or why not?
