# JpaRepository and the repository hierarchy

You have an `@Entity`. You need a way to save it, find it, delete it. JPA's raw answer is `EntityManager`. Spring Data JPA's answer is one line: declare an interface that extends `JpaRepository<Book, Long>` and never write the implementation. This topic is about what that interface gives you, what the lower interfaces in the hierarchy give you, and how to pick.

---

## The problem this solves

Without Spring Data JPA, every entity needs a hand-written DAO:

```java
public class BookDao {
    @PersistenceContext private EntityManager em;

    @Transactional
    public Book save(Book b) {
        if (b.getId() == null) em.persist(b);
        else b = em.merge(b);
        return b;
    }
    public Optional<Book> findById(Long id) {
        return Optional.ofNullable(em.find(Book.class, id));
    }
    public List<Book> findAll() {
        return em.createQuery("SELECT b FROM Book b", Book.class).getResultList();
    }
    @Transactional
    public void deleteById(Long id) {
        em.find(Book.class, id).ifPresent(em::remove);
    }
    public long count() {
        return em.createQuery("SELECT COUNT(b) FROM Book b", Long.class).getSingleResult();
    }
    // ...
}
```

Times every entity. All of that code is *the same* except for the class name (`Book` here, `Author` next file). Spring Data JPA replaces it with:

```java
public interface BookRepository extends JpaRepository<Book, Long> {}
```

One line. No implementation. Spring generates the proxy at startup, plugged into your `EntityManager`, and you inject `BookRepository` like any other bean.

---

## The hierarchy

`JpaRepository` is at the top of a chain of interfaces, each adding methods on top of the previous one. From most general to most specific:

```text
Repository<T, ID>                              <- marker only; no methods.
   |
   v
CrudRepository<T, ID>                          <- save, findById, findAll, delete, count, ...
   |
   v
PagingAndSortingRepository<T, ID>              <- adds findAll(Pageable), findAll(Sort)
   |
   v
ListCrudRepository<T, ID>                      <- adds list-returning variants
ListPagingAndSortingRepository<T, ID>          <- adds list-returning paging/sorting
   |
   v
JpaRepository<T, ID>                           <- adds flush, saveAndFlush, deleteAllInBatch,
                                                  getReferenceById, and JPA-flavored returns
```

You pick **one** interface to extend. The methods of every interface above it are inherited.

| Interface | What it adds | Pick this when |
|---|---|---|
| `Repository<T, ID>` | Nothing (marker). | You want only custom methods, no CRUD. Rare. |
| `CrudRepository<T, ID>` | `save`, `saveAll`, `findById`, `existsById`, `findAll`, `findAllById`, `count`, `deleteById`, `delete`, `deleteAll`. | You want CRUD only, no paging. Datastore-agnostic (works with Mongo, Cassandra, etc.) — useful if you might swap stores. |
| `PagingAndSortingRepository<T, ID>` | Adds `findAll(Sort)` and `findAll(Pageable)`. | You need paging/sorting on top of CRUD. Still datastore-agnostic. |
| `JpaRepository<T, ID>` | Adds JPA-specific methods (`flush`, `saveAndFlush`, `getReferenceById`, `deleteAllInBatch`) and tightens return types to `List<T>` from `Iterable<T>`. | You know you're on JPA. Almost every Spring Boot service. |

In practice: **extend `JpaRepository` by default.** Drop down to `CrudRepository` only if you have a real reason (a service that might switch from JPA to Mongo). The cost of `JpaRepository` over `CrudRepository` is zero — it just gives you more.

---

## How it works

Look at the file `CODE_EXAMPLES/03-jparepository-and-the-hierarchy/repository-demo/BookRepository.java`:

```java
public interface BookRepository extends JpaRepository<Book, Long> {
}
```

That's the whole file. No `@Repository` annotation, no `BookRepositoryImpl`. At startup, Spring Data JPA:

1. Scans for interfaces that extend `Repository` (transitively).
2. For each one, generates a proxy class at runtime that implements the interface.
3. Registers the proxy as a Spring bean keyed on the interface name.
4. Wires every inherited method to a JPA-backed implementation (`save` → `EntityManager.persist`/`merge`, `findById` → `EntityManager.find`, etc.).

You then inject the interface:

```java
@Autowired
private BookRepository repository;
```

…and call methods. Spring's proxy intercepts each call, opens a transaction if needed, executes the JPA work, commits.

### The methods you get from the start

From `BookRepositoryTest.java`, every assertion below works on the bare `BookRepository extends JpaRepository<Book, Long>` with **no custom code**:

```java
Book saved = repository.save(new Book("Effective Java", "Joshua Bloch", 2017));
// id assigned, INSERT issued (or batched at flush)

Optional<Book> found = repository.findById(saved.getId());
// SELECT ... WHERE id = ?

List<Book> all = repository.findAll();
// SELECT ... FROM books

List<Book> byYear = repository.findAll(Sort.by("publishedYear").ascending());
// SELECT ... ORDER BY published_year ASC

List<Book> bulk = repository.saveAll(List.of(b1, b2));
// two INSERTs, batched if possible

boolean exists = repository.existsById(42L);
// SELECT COUNT(*) WHERE id = ? (or similar)

long total = repository.count();
// SELECT COUNT(*) FROM books

repository.deleteById(42L);
// DELETE FROM books WHERE id = ?
```

### Dirty checking — the update you didn't write

`JpaRepository` does **not** have an `update` method. There's `save`, but no separate `update`. How do updates work?

```java
Book managed = repository.findById(42L).orElseThrow();
managed.setTitle("Effective Java, 3rd Edition");
// no save() call here
```

Inside a transaction, the `Book` returned by `findById` is a **managed entity** — Hibernate tracks every field. When the transaction commits (or flushes), Hibernate compares the current field values against a snapshot it kept and emits an UPDATE for any changes. This is called **dirty checking**, and it's one of the reasons JPA exists.

The test `updateThroughDirtyChecking` shows it: load, mutate, reload — and the change is there, without ever calling `save()`.

Outside a transaction (or after `em.clear()`) the entity is **detached**, and dirty checking does not apply. Setting a field has no effect until you `save(detached)`, which translates to a `merge` and re-attaches.

---

## `save` vs `saveAndFlush` vs `persist`

These are different operations. Knowing the difference avoids mysterious "I called save but the SQL hasn't run yet" debugging.

| Method | What it does | When the SQL runs |
|---|---|---|
| `repository.save(entity)` | Either `persist` (if new) or `merge` (if existing). Returns the managed entity. | At the next flush — typically transaction commit. |
| `repository.saveAndFlush(entity)` | Same as `save` but forces a flush immediately. | Right now, before the method returns. |
| `entityManager.persist(entity)` | Make a new entity managed. Throws if the entity already has an id and is detached. | At the next flush. |
| `entityManager.merge(entity)` | Copy a detached entity's state into a managed copy. Returns the managed copy. | At the next flush. |

In a typical service method (which is wrapped by `@Transactional`), `save` is the default. Reach for `saveAndFlush` only when you need the SQL *now* — for example, to surface a constraint violation before the transaction commits, or to make a generated id visible to other JDBC code in the same transaction.

---

## `getReferenceById` — a hidden footgun

`JpaRepository` has both:

- `findById(id)` → returns `Optional<T>`, executes a SELECT immediately to confirm the row exists.
- `getReferenceById(id)` → returns `T`, returns a *proxy* that **does not execute SQL until you touch a field**.

`getReferenceById` is useful when you're about to set a foreign-key relationship and only need the id:

```java
Author author = authorRepository.getReferenceById(42L);  // no SELECT
Book book = new Book("...", "...", 2020);
book.setAuthor(author);
bookRepository.save(book);                                // INSERT with author_id = 42
```

…but it's a footgun if you forget the proxy is lazy. Touching any other field of `author` outside the transaction throws `LazyInitializationException`. Touching it inside the transaction issues a SELECT you didn't expect. Topic 07 covers this in depth.

Rule of thumb: prefer `findById` unless you have a measured reason to use `getReferenceById`.

---

## A concrete look — what each layer adds

If you wanted, you could extend `CrudRepository` instead:

```java
public interface BookRepository extends CrudRepository<Book, Long> {}
```

You'd still have `save`, `findById`, `findAll`, `count`, `deleteById`. What you'd **lose**:

- `findAll()` returns `Iterable<Book>` instead of `List<Book>` — every call site needs `.iterator()` or a loop.
- No `findAll(Sort)` or `findAll(Pageable)` — paging and sorting need separate code.
- No `flush()`, `saveAndFlush()`, `getReferenceById()`, `deleteAllInBatch()`.

The list-returning fix is `ListCrudRepository<T, ID>` (added in Spring Data 3.0), if you want the smaller surface but with `List` returns. But once you've climbed that far, `JpaRepository` is one more step up with no extra cost.

---

## Common pitfalls

- **Adding `@Repository` on the interface.** Old Spring tutorials show `@Repository public interface BookRepository ...`. It's harmless but unnecessary — Spring Data JPA picks up the interface from the inheritance chain. Fix: drop the annotation.
- **Writing a `BookRepositoryImpl` class.** Spring Data JPA generates the implementation. If you write your own, you'll either fight the framework or shadow methods you didn't mean to. (There is a `Custom` interface pattern for extending with hand-written code — see [Spring Data docs](https://docs.spring.io/spring-data/jpa/reference/) — but the plain "implement everything yourself" path is wrong.)
- **Using `findById(id).get()` everywhere.** That throws `NoSuchElementException` for missing rows, which becomes a 500 by default. Use `.orElseThrow(...)` with a meaningful exception, or `.orElse(null)`, or pattern-match the Optional. Mapping the missing case to a 404 is module 11's job.
- **Calling `repository.save()` and expecting the SQL to run immediately.** It doesn't — it runs at the next flush, which is usually transaction commit. Use `saveAndFlush` if you need it now.
- **Mutating a returned entity outside the transaction and expecting it to persist.** Dirty checking only works on **managed** entities **inside a transaction**. Outside, you need an explicit `save(detached)`.
- **Picking `CrudRepository` "to keep things minimal."** You give up sorting and paging for nothing. `JpaRepository` is the same speed at runtime.

---

## Try this yourself

1. Change `BookRepository extends JpaRepository<Book, Long>` to `extends CrudRepository<Book, Long>` and re-run the tests. Two tests fail — `findAllWithSortOrdersResults` (no `findAll(Sort)` on `CrudRepository`) and the type of `findAll()` itself. Read the errors and put the test back the way it was.
2. Replace one `repository.findById(id)` call in the test with `repository.getReferenceById(id)`. Inspect the SQL in the console. Notice no SELECT happens at the call site — only when you touch a field. Outside `@Transactional`, you'd get `LazyInitializationException`.
3. Add `repository.saveAndFlush(...)` after a `save(...)`. Watch the difference in the console: with `save`, the INSERT runs at commit; with `saveAndFlush`, it runs immediately.

## Self-check

1. You write `interface BookRepository extends JpaRepository<Book, Long>` and never write an implementation class. Who provides the implementation, and when?
2. What is *dirty checking* and why does it mean a service method that loads, mutates, and returns does not need to call `save()`?
3. Give one concrete reason to extend `JpaRepository` instead of `CrudRepository`.
4. `findById(id)` returns `Optional<Book>`. `getReferenceById(id)` returns `Book`. What is the operational difference between the two, and when would you prefer the latter?
