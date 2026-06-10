# Auditing

Every production data model eventually needs the same four columns on every table: when was this row created, when was it last updated, by whom each time. Writing them by hand on every entity is duplicative and easy to forget. Spring Data JPA's auditing feature wires the four columns automatically — you declare the fields, mark them with annotations, and they fill themselves in.

---

## The problem this solves

Without auditing, every entity ends up with this scaffolding:

```java
@PrePersist
void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.createdBy = SecurityContextHolder.getContext().getAuthentication().getName();
    this.updatedBy = this.createdBy;
}

@PreUpdate
void onUpdate() {
    this.updatedAt = Instant.now();
    this.updatedBy = SecurityContextHolder.getContext().getAuthentication().getName();
}
```

Times every entity. Twenty entities, twenty copy-pastes of the same lifecycle methods, each a place for a bug. Spring Data JPA replaces all of that with declarative annotations.

---

## How it works

Three pieces wire it up.

**1. Enable auditing on the application:**

```java
@SpringBootApplication
@EnableJpaAuditing
public class App { ... }
```

`@EnableJpaAuditing` activates the `AuditingEntityListener` infrastructure.

**2. Annotate each entity with the listener and the four fields:**

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Book {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ...
}
```

`updatable = false` on the "created" columns is the safety net — even if some code path tries to overwrite `createdAt` after insert, the column will not change.

**3. Provide an `AuditorAware<T>` bean for the "by" fields:**

```java
@Bean
public AuditorAware<String> auditorAware() {
    return () -> Optional.of("test-user");
}
```

In a real app this looks at Spring Security:

```java
@Bean
public AuditorAware<String> auditorAware() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName);
}
```

When auditing fires, it asks the `AuditorAware` for the "current user." If the bean returns `Optional.empty()`, the `@CreatedBy` / `@LastModifiedBy` fields are left null.

---

## What gets set, when

| Event | Fields touched |
|---|---|
| `INSERT` (entity is new — first `save`) | `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy` |
| `UPDATE` (entity is managed and dirty, or detached and `merge`d with changes) | `@LastModifiedDate`, `@LastModifiedBy` |
| `DELETE` | Nothing — Spring Data JPA doesn't track soft-delete by default. |

The demo's two tests show this:

```java
@Test
void createdFieldsAreSetOnInsert() {
    Book saved = repository.saveAndFlush(new Book("Effective Java"));
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getCreatedBy()).isEqualTo("test-user");
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
}

@Test
void updatedFieldsChangeOnSave_createdFieldsDoNot() throws InterruptedException {
    Book saved = repository.saveAndFlush(new Book("Original"));
    Instant originalCreated = saved.getCreatedAt();
    Instant originalUpdated = saved.getUpdatedAt();

    Thread.sleep(10);
    saved.setTitle("Updated");
    repository.saveAndFlush(saved);

    Book reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreated);   // immutable
    assertThat(reloaded.getUpdatedAt()).isAfter(originalUpdated);     // advanced
}
```

`createdAt` is set once, never updates. `updatedAt` advances on every change.

---

## Field types

`@CreatedDate` and `@LastModifiedDate` accept several time types:

- `Instant` — UTC, no zone. **Best default.** Times stored in UTC; render to whatever the client wants.
- `LocalDateTime` — timezone-free. Avoid: silently uses the JVM's default zone.
- `ZonedDateTime` / `OffsetDateTime` — explicit zone. Useful if you genuinely need to preserve the originating zone.
- `Date` / `java.sql.Timestamp` — legacy. Avoid in new code.
- `Long` (epoch millis) — works but unfriendly to read in DB clients.

For `@CreatedBy` / `@LastModifiedBy`, the type matches whatever your `AuditorAware<T>` returns — usually `String` (the username), sometimes a `Long` (the user id), sometimes a richer type with a `@ManyToOne User auditor` if you want navigation.

---

## Putting the audit fields into a base class

A common pattern: pull the four fields onto a `@MappedSuperclass` and have each entity extend it.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {
    @CreatedDate    @Column(nullable = false, updatable = false) protected Instant createdAt;
    @LastModifiedDate @Column(nullable = false)                  protected Instant updatedAt;
    @CreatedBy      @Column(length = 100, updatable = false)     protected String createdBy;
    @LastModifiedBy @Column(length = 100)                        protected String updatedBy;
    // getters
}

@Entity
public class Book extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    // ...
}
```

`@MappedSuperclass` is *not* an `@Entity` — there's no `auditable` table. Each subclass inherits the columns and the listener. Twenty entities, one source of truth for audit columns.

---

## Common pitfalls

- **Forgetting `@EnableJpaAuditing`.** Without it, the `AuditingEntityListener` doesn't run and your audit fields stay null. There is **no** error message. The first sign is `createdAt: null` rows in the database. Add `@EnableJpaAuditing` on the `@SpringBootApplication` class.
- **Forgetting `@EntityListeners(AuditingEntityListener.class)`.** Same null result, for one entity only. Either add it on every entity, or put it on a `@MappedSuperclass` shared base.
- **Not providing an `AuditorAware`.** `@CreatedBy` / `@LastModifiedBy` stay null with no error. Either supply the bean, or remove those fields.
- **`AuditorAware` returns the wrong thing.** A common bug: the bean returns `"anonymousUser"` when no authentication is present (Spring Security's default principal name). Filter explicitly: `.filter(Authentication::isAuthenticated)` and skip the anonymous case.
- **Using `LocalDateTime` and getting confused timestamps.** `LocalDateTime` has no zone, so values stored from a UTC server and read from a non-UTC client look "off." Use `Instant`.
- **Test isolation.** Tests that depend on `Thread.sleep(10)` to assert "updatedAt advanced" are fragile on fast machines. Either accept the 10ms tax, or compare timestamps with a tolerance.
- **Cleartext audit on soft-deleted rows.** Spring Data JPA's auditing fires on insert/update — it does *not* track delete. If you need a delete audit trail, add a `deletedAt` column and use a soft-delete approach, or wire a custom listener.

---

## Try this yourself

1. Remove `@EnableJpaAuditing` from `App.java`. Re-run — the test fails because `createdAt` is null. Add it back. There was no error message, only a null column.
2. Change the `AuditorAware` bean to return `Optional.empty()`. Re-run — `createdBy` is null. Spring doesn't complain; the bean is optional.
3. Pull the four audit fields into a `@MappedSuperclass Auditable`. Have `Book` extend it. Verify all tests still pass and the columns appear on the `books` table (no new table).
4. Add a `Tag` entity that also extends `Auditable`. Verify it inherits the audit columns and the listener — no additional annotation needed on `Tag` itself.

## Self-check

1. What three pieces wire JPA auditing in a Spring Boot app?
2. What does `updatable = false` do on a `@CreatedDate` column, and why is it useful?
3. Without `@EnableJpaAuditing`, what happens when you save an entity that has `@CreatedDate` and `@LastModifiedDate` fields?
4. In a real app, how does the `AuditorAware<String>` bean usually find the current user?
