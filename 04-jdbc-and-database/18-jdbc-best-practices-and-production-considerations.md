# 18 — JDBC Best Practices and Production Considerations

You've now seen every individual technique. This topic ties them together into the habits that separate code that "works in dev" from code that survives production.

Nothing new here in the API — just the discipline.

---

## Checklist: the 10 commandments of production JDBC

1. **Every connection, statement, and ResultSet is in `try-with-resources`.** No exceptions.
2. **Use `PreparedStatement` for anything with values.** Plain `Statement` only for fixed DDL.
3. **Use a connection pool.** `HikariCP`. Always. Never `DriverManager` directly in request-handling code.
4. **Don't hardcode credentials.** Load them from environment variables, secrets manager, or config files outside the code.
5. **Multi-statement work goes in an explicit transaction** with rollback on exception.
6. **Keep transactions short.** No slow operations (external APIs, file I/O) inside a transaction.
7. **Check return values.** `executeUpdate` returns row count. If you ignore it, you can't detect "the WHERE matched nothing" bugs.
8. **Log slow queries.** A query that's fine at 100 rows may be terrible at 1M.
9. **Use migrations** (Flyway, Liquibase) for schema changes. Don't run DDL from application code in production.
10. **Don't catch and silently swallow `SQLException`.** Log it with context; re-throw or convert to a meaningful application exception.

---

## Configuration: where credentials and URLs live

Hardcoded:

```java
// BAD
String URL = "jdbc:mysql://prod-db.example.com:3306/app";
String USER = "appuser";
String PASS = "Pa$$w0rd123";    // committed to git, visible in every PR
```

This is wrong in several ways at once:

- Passwords end up in git history forever.
- Different environments (dev, staging, prod) require code changes.
- Rotating the password requires a redeploy.

Better:

```java
String URL  = System.getenv("DB_URL");
String USER = System.getenv("DB_USER");
String PASS = System.getenv("DB_PASSWORD");
```

The values come from environment variables set by your deploy pipeline. In production, a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.) sets these for you. **The code never sees the actual values.**

In Spring Boot you'd put these in `application.yml` and let Spring inject them, but the principle is the same: separation between code and secrets.

---

## SQLException: more than just "something broke"

Every JDBC method throws `SQLException`. Inside it, you can find:

- `getMessage()` — human-readable description
- `getSQLState()` — five-character standard-ish code (`23000` = integrity constraint violation, `08000` = connection error, etc.)
- `getErrorCode()` — vendor-specific numeric code
- `getNextException()` — chained exceptions for batched failures

```java
try {
    ps.executeUpdate();
} catch (SQLException e) {
    log.error("Insert failed: state={}, code={}, msg={}",
            e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
    throw new DataAccessException("could not save user", e);
}
```

You usually wrap `SQLException` in your own exception type (`DataAccessException`, `RepositoryException`, etc.) so callers don't have to import `java.sql` everywhere.

### Common SQL state codes worth recognizing

| State | Meaning |
|-------|---------|
| `23000`, `23505` | Unique constraint violation (duplicate key) |
| `23502` | Not-null violation |
| `23503` | Foreign key violation |
| `08000`, `08006` | Connection failure |
| `40001` | Serialization failure (concurrent update conflict, retry candidate) |
| `42000` | Syntax error in SQL |

You don't need to memorize these. Bookmark a reference and look them up when you see one.

---

## When to retry, when to fail

Some `SQLException`s are transient (network blip, deadlock, brief unavailability). Some are deterministic (syntax error, missing table). Retrying a deterministic error wastes time and produces the same result.

**Retry candidates:**

- Connection errors (`08*`)
- Deadlock / serialization conflicts (`40001` or vendor-specific)
- Pool timeout, if the failure was likely a brief spike

**Never retry:**

- Constraint violations (`23*`) — re-running won't fix bad data
- Syntax errors (`42*`) — won't fix themselves
- Authentication failures

Retry strategy: small number of attempts (3), with exponential backoff (e.g. 100ms, 500ms, 2s). Log every retry. Give up clearly.

---

## Logging SQL and parameters

Useful for debugging:

```java
log.debug("Executing: {} with params [{}, {}]", sql, name, email);
```

**But** parameters often include personal data (PII): emails, names, IDs that map to users. Be careful:

- Don't log PII at INFO or WARN (where it's persisted long-term).
- Don't log password parameters at any level.
- Consider hashing or truncating sensitive fields if they must appear in debug logs.

In production: have SQL-level slow-query logging on the database side (`log_min_duration_statement = 1000` in PostgreSQL, etc.). It captures the actual queries the database saw, with timing.

---

## Don't run DDL from application code

A classic anti-pattern:

```java
@PostConstruct
void init() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS users ...");
    jdbc.execute("ALTER TABLE users ADD COLUMN ...");
}
```

Tempting but wrong:

- DDL needs to run *once*, not on every app startup.
- Multiple instances starting simultaneously fight over the DDL.
- Schema changes need code review, not Java conditionals.
- Rolling back a DDL change is much harder than rolling back a code change.

**Use a migration tool:**

- **Flyway** — versioned SQL files in `db/migration/`. Runs new ones, tracks state in a `flyway_schema_history` table.
- **Liquibase** — XML/YAML changesets. More features, more ceremony.

Both let you commit schema changes to git, review them in PRs, and have predictable execution order.

For this learning module we run DDL from `main` for simplicity. **Don't do that in production.**

---

## A note on Statement vs query timing

A correctness pattern: **use `setQueryTimeout(seconds)`** on statements that should not run forever.

```java
ps.setQueryTimeout(5);    // give up after 5 seconds
```

Otherwise a runaway query (missing index, accidental cross join, unexpected data shape) can lock up your request thread until the database itself decides to give up — often many minutes.

In Spring, you set this via `@Transactional(timeout = 5)` or `JdbcTemplate.setQueryTimeout(5)`.

---

## What you'll meet next

This module ends here. The next layers up — Spring `JdbcTemplate` (module 09), Spring Data JPA (module 10), Hibernate — all build on what you've learned. They reduce boilerplate, but they don't change the underlying truths:

- Connections are still expensive (the pool still matters).
- SQL injection is still possible if you misuse the high-level API.
- Transactions still need a clear boundary.
- Leaks still happen if you don't return resources.

**Every "Hibernate is mysterious" debugging session ends in a JDBC reality check.** Knowing the layer underneath is what makes the layers above debuggable.

---

## Code examples

1. `FullProductionShape.java` — putting it all together: pool, prepared statements, transactions, proper error handling. A single self-contained example showing the whole production shape.
2. `ExceptionHandling.java` — read `SQLException` properly: state, code, chained exceptions.
3. `CredentialsFromEnvironment.java` — load DB credentials from env vars, with a clear failure if missing.
4. `QueryTimeoutAndRetry.java` — set query timeouts; demonstrate a simple retry-on-transient-error pattern.

---

## Try this yourself

1. Run `FullProductionShape.java`, then go through and identify which line corresponds to each of the 10 commandments above.
2. In `ExceptionHandling.java`, deliberately violate a unique constraint and inspect the SQL state.
3. In `CredentialsFromEnvironment.java`, unset one env var and run. Note the error.

---

## Self-check

1. List five of the ten commandments without looking. (Aim for the most important ones first.)
2. Why is hardcoding credentials in code bad even for "internal" or "small" projects?
3. A query is taking 30 seconds and timing out the user-facing request. What two things would you investigate first?
