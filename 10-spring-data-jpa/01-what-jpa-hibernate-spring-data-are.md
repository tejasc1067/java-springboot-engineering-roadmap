# What JPA, Hibernate, and Spring Data JPA are

Three names that get used interchangeably in conversation, in tutorials, and in job postings — and they are not the same thing. You will not be able to read the rest of this module, or other people's Spring Boot code, until you can say which layer does what.

This is the only markdown-only topic in module 10. From topic 02 onward, every page comes with a runnable demo.

---

## The problem this solves

You wrote raw JDBC in module 04. The code worked, but it was tedious:

```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "SELECT id, title, author FROM book WHERE id = ?")) {
    ps.setLong(1, id);
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            Book b = new Book();
            b.setId(rs.getLong("id"));
            b.setTitle(rs.getString("title"));
            b.setAuthor(rs.getString("author"));
            return b;
        }
        return null;
    }
}
```

Multiply that by every entity, every query, every join. Now multiply by the number of `setX(rs.getX(...))` lines you have to keep in sync with the database schema every time it changes. Every team that wrote a backend in Java between 2000 and 2005 felt this pain and built or adopted a tool to remove it.

The tool category is **object-relational mapping** — ORM. The idea: declare that a `Book` class maps to a `book` table, declare that the `id` field maps to the `id` column, and let a library generate the SQL.

---

## The three layers, from bottom to top

When you depend on `spring-boot-starter-data-jpa` in your `pom.xml`, you are pulling in three distinct things stacked on top of JDBC.

```text
+-------------------------------------+
| Spring Data JPA                     |  <- you write: JpaRepository<Book, Long>
| (repository abstractions, derived   |     it writes: queries, transactions,
|  queries, pagination, specs)        |     pagination, dynamic dispatch
+-------------------------------------+
| Hibernate                           |  <- the engine that actually maps
| (JPA implementation: turns @Entity  |     @Entity classes to SQL,
|  + queries into SQL, manages the    |     manages the cache/dirty checking
|  persistence context)               |
+-------------------------------------+
| JPA (Jakarta Persistence API)       |  <- the *specification*: annotations
| (jakarta.persistence.*: @Entity,    |     and interfaces only. No code that
|  @Id, EntityManager, JPQL grammar)  |     does anything by itself.
+-------------------------------------+
| JDBC                                |  <- still down here, doing the actual
| (Connection, PreparedStatement,     |     network call to the database.
|  ResultSet, DataSource)             |
+-------------------------------------+
```

### Layer 1: JPA — the specification

**JPA** (Jakarta Persistence API, previously Java Persistence API) is a **specification**. It is a set of Java interfaces and annotations published in the `jakarta.persistence` package (and `javax.persistence` on older Spring Boot versions). It contains *no* working code on its own — only the contract.

The pieces of JPA you will use most:

- Annotations: `@Entity`, `@Id`, `@GeneratedValue`, `@Column`, `@Table`, `@OneToMany`, `@ManyToOne`, `@JoinColumn`, `@Transient`.
- Interfaces: `EntityManager`, `EntityManagerFactory`, `EntityTransaction`.
- A query language: **JPQL** (Java Persistence Query Language) — looks like SQL but operates on entity *classes and fields*, not tables and columns.

JPA was originally published as JSR 220 (part of EJB 3.0, 2006), spun out as JSR 317 (JPA 2.0, 2009), and is now maintained by the Eclipse Foundation under the **Jakarta EE** umbrella (the rename from "Java" to "Jakarta" in 2019 is the reason you see both `javax.persistence` and `jakarta.persistence` in the wild — same API, new package).

You can think of JPA as the "USB-C of Java persistence": a plug shape. It does not contain electricity. You still need a thing on the other end that actually does work — an *implementation*.

### Layer 2: Hibernate — one implementation of JPA

**Hibernate** is the most popular JPA implementation. It was created by Gavin King in 2001, *before* JPA existed; when JPA was being standardized, Hibernate's API was a major influence on the spec. Today Hibernate is a JPA-compliant implementation plus extensions.

Hibernate is the layer that actually:

- Reads your `@Entity` annotations at startup and builds an in-memory model of which class maps to which table.
- Generates SQL for inserts, updates, deletes, and JPQL queries.
- Manages the **persistence context** — an in-memory cache of entities loaded in the current transaction, with **dirty checking** (it notices when you `book.setTitle("...")` and writes an UPDATE at flush time, without you calling `save()`).
- Implements lazy loading (a `@ManyToOne Author` field that doesn't get fetched until you read it).
- Coordinates with the JDBC layer to actually run the SQL.

Other JPA implementations exist — **EclipseLink** (reference implementation, used in Jakarta EE servers) and **OpenJPA** (Apache, less common now). You will almost never see them in Spring Boot apps. Spring Boot's autoconfiguration assumes Hibernate.

The crucial point: **when something works "by JPA magic," it's actually Hibernate doing the work.** When you read a stack trace and see `org.hibernate.LazyInitializationException`, that is Hibernate-the-implementation telling you about a problem; the *JPA spec* does not define that exception. Knowing which layer threw the error is half of debugging.

### Layer 3: Spring Data JPA — repositories without implementations

**Spring Data JPA** is a Spring project (not part of JPA, not part of Hibernate) that adds the **repository abstraction** on top.

The repository abstraction lets you declare an interface:

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByAuthor(String author);
    long countByPublishedYear(int year);
}
```

…and Spring Data JPA, at startup, **generates an implementation** at runtime. You never write `BookRepositoryImpl`. The implementation:

- Inherits the standard CRUD methods (`save`, `findById`, `findAll`, `delete`, etc.) from `JpaRepository`.
- Parses each custom method name (`findByAuthor`, `countByPublishedYear`) and turns it into a JPQL query.
- Wraps every method in a transaction (read-only for finders, read-write for `save` / `delete`).
- Adds pagination, sorting, and `Specification` support if your interface declares them.

Spring Data JPA does not replace Hibernate — it sits *on top* of Hibernate and uses it to actually run the queries. You can use plain JPA (`EntityManager`, `entityManager.find(Book.class, id)`) inside a Spring Boot app and it will work; Spring Data JPA is just the layer that saves you from writing that boilerplate.

---

## A worked example: the same query in all three layers

Same goal: fetch a book by id.

**JDBC (module 04 style):**

```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "SELECT id, title, author FROM book WHERE id = ?")) {
    ps.setLong(1, id);
    try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? mapRow(rs) : null;
    }
}
```

**JPA directly (`EntityManager`):**

```java
@PersistenceContext
private EntityManager em;

public Book findOne(Long id) {
    return em.find(Book.class, id);  // Hibernate generates SELECT under the hood
}
```

**Spring Data JPA:**

```java
public interface BookRepository extends JpaRepository<Book, Long> {}

// in a service
Book b = bookRepository.findById(id).orElse(null);
```

All three send the same SQL to the database. The difference is how much code you wrote, and how testable that code is.

---

## Why this stacking matters

If you only ever use Spring Data JPA, you can go a long way without thinking about the layers. Then one of these will happen:

1. **You see `LazyInitializationException` in production.** That class lives in `org.hibernate`. You need to know that Hibernate, not Spring, decides when to load relationships, and that Spring Data JPA does not change that. Topic 07.

2. **You see a `MethodNotAllowed` for a `save()` outside a transaction.** That is the JPA spec saying "you cannot modify managed entities outside a transaction." Spring Data JPA gives you a `@Transactional` wrapper *on the repository's own methods* — but if you call `entity.setFoo(...)` outside of any transaction and don't call `save()`, nothing happens, and the silence will baffle you. Topic 08.

3. **A derived query method is taking 30 seconds.** The fix is at the *Hibernate* layer (look at the generated SQL, find the missing index, or add `@EntityGraph` to avoid lazy loading) — not at the Spring Data JPA layer. Knowing which layer to look at saves hours.

4. **An interview asks "what is JPA?"** Saying "it's Spring Data JPA" is wrong. Saying "it's a specification; Hibernate is the implementation; Spring Data JPA is a repository abstraction on top of JPA" is the answer.

---

## When to use it / when not to

JPA + Spring Data JPA is the **right default** for Spring Boot services that own a relational database, have an entity model that isn't insanely write-heavy, and where developer productivity matters.

It is the **wrong choice** when:

- **You need every query to be a hand-tuned SQL statement.** High-throughput analytics, complex reporting queries with window functions, anything where the SQL is the product. Use `JdbcTemplate` or jOOQ.
- **Your "objects" are not really objects.** If your data is event-shaped (append-only logs, time series), forcing it into `@Entity` classes adds friction and removes leverage. Use a different store or a different access layer.
- **You need bulk writes at scale.** JPA's persistence context is *per-row* — every insert ends up as an INSERT statement (batched, but still). Loading 10 million rows through JPA is a memory and time mistake. Use a streaming JDBC writer, or `COPY` (Postgres) / `LOAD DATA` (MySQL) directly.
- **You need reactive, non-blocking persistence.** JPA's API is synchronous by design. Spring Data R2DBC is a separate, narrower alternative for the reactive stack — but you give up most of the JPA features.

A useful rule: **if you find yourself writing native SQL for every repository method**, you have outgrown JPA for that service. Use `JdbcTemplate` and own your SQL directly.

---

## Common pitfalls

- **"JPA = Hibernate = Spring Data JPA."** Three different things. Knowing which is which is the entire point of this topic.
- **Using `javax.persistence.*` imports in a Spring Boot 3 project.** Spring Boot 3 moved to `jakarta.persistence.*` along with the rest of the Jakarta EE rename. Old tutorials and Stack Overflow answers will paste `javax.persistence` and your code will not compile. Fix: change every `javax.persistence` import to `jakarta.persistence`.
- **Thinking the database schema is the source of truth for the entity model (or vice versa).** Either is fine, but pick one. Topic 12 (Flyway) is built around "SQL migrations are the source of truth and the entity model follows."
- **Mixing `EntityManager` and `JpaRepository` in the same service and not knowing which one is active.** Both are valid; both can coexist; both share the same persistence context within a transaction. Pick one for each service method to avoid confusion.

---

## Try this yourself

1. Open the `pom.xml` of any Spring Boot project you have used and find `spring-boot-starter-data-jpa`. Run `mvn dependency:tree | grep -E "hibernate|jpa"`. Read the output and label which line is JPA-the-spec, which is Hibernate, and which is Spring Data JPA.
2. Open Hibernate's source on GitHub (`hibernate/hibernate-orm`) and find one class that implements a `jakarta.persistence` interface. Note that the JPA repo and the Hibernate repo are separate projects with separate release cycles.
3. Look at a `JpaRepository` source file in your IDE (`Ctrl+click` on `JpaRepository` in any project). Notice it is an **interface** — Spring Data JPA generates the implementation at runtime. There is no `JpaRepositoryImpl` you wrote.

## Self-check

1. A colleague says "we use JPA in production." What follow-up question do you ask to know what they actually mean?
2. Where does `LazyInitializationException` come from — the JPA spec, Hibernate, or Spring Data JPA? Why does that matter?
3. You write `interface BookRepository extends JpaRepository<Book, Long>` and never write an implementation class. Who provides the implementation, and when?
4. Give one task you would *not* use JPA for, and what you would use instead.
