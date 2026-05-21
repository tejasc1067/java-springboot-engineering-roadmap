# 12 — JDBC: Introduction and Architecture

You've been writing JDBC code since topic 05 to run SQL against H2. This topic finally explains what JDBC *is*, what each piece does, and why the API looks the way it does.

JDBC ("Java Database Connectivity") is the standard Java API for talking to relational databases. Every Java backend framework — Spring, Hibernate, MyBatis, R2DBC — is ultimately built on JDBC (or its async cousin). Understanding the layer below saves you when frameworks misbehave.

---

## The architecture in one picture

```
+--------------------+
| Your Java code     |
|   Connection conn  |
|   Statement stmt   |
|   ResultSet rs     |
+----------+---------+
           |
           v
+--------------------+
| JDBC API           |   ← the interfaces you call (java.sql.*)
+----------+---------+
           |
           v
+--------------------+
| JDBC Driver        |   ← vendor-specific implementation
| (h2.jar, etc.)     |     loaded at runtime
+----------+---------+
           |
   network (or in-process for H2)
           |
           v
+--------------------+
| Database server    |
+--------------------+
```

The point: **JDBC is an interface, not an implementation.** Your code calls `conn.createStatement()` — the *driver* (H2's jar, PostgreSQL's jar, MySQL's jar) provides the implementation. Same code, different driver, different database. You can switch from H2 to PostgreSQL by changing one URL and one jar.

---

## The four core types

JDBC's everyday API boils down to four interfaces:

| Type | What it is | Lifetime |
|------|-----------|----------|
| `DriverManager` | Factory that hands out connections | Static (singleton-ish) |
| `Connection` | An open session with the database | One per "session" of work |
| `Statement` / `PreparedStatement` | A SQL query you can execute | One per query (usually) |
| `ResultSet` | The rows returned by a query | One per query result |

You'll use these four for the rest of the module. Other types exist (`CallableStatement` for stored procedures, `DatabaseMetaData` for introspection) but these four are 95% of daily JDBC.

---

## DriverManager: getting a connection

```java
String url = "jdbc:h2:mem:demo";
Connection conn = DriverManager.getConnection(url, "sa", "");
```

`DriverManager` is the bootstrap. You give it a URL, username, password. It scans all loaded JDBC drivers, finds the one that handles your URL's protocol (`jdbc:h2:` → H2's driver), asks it to make a connection, and hands it back to you.

In Java 6+ drivers are auto-loaded via the `ServiceLoader` mechanism — just having `h2.jar` on the classpath is enough. You don't need the old `Class.forName("...")` ritual.

### Connection URL anatomy

URLs vary by database:

```
jdbc:h2:mem:demo                                  ← H2 in-memory
jdbc:h2:./mydatabase                              ← H2 file-based
jdbc:postgresql://localhost:5432/mydb             ← PostgreSQL
jdbc:mysql://db.example.com:3306/mydb?ssl=true    ← MySQL
```

All start with `jdbc:`. The next segment names the driver. The rest is driver-specific.

---

## Connection: the session

A `Connection` represents an open TCP session (or in-process channel) to the database. Through it you can:

- Create statements to run queries.
- Manage transactions (`setAutoCommit`, `commit`, `rollback`).
- Configure isolation level, schema, etc.

**Connections are expensive.** Opening one involves a TCP handshake, authentication, possibly TLS negotiation. **You don't open a fresh connection per query** in production — you use a *connection pool* (topic 17). For now, each example opens its own connection because it's a single-shot script.

**Connections must be closed.** Forgetting `conn.close()` is the #1 source of resource leaks. `try-with-resources` (topic 13) makes this automatic.

---

## Statement and PreparedStatement: queries

Two flavors:

- `Statement` — execute a literal SQL string. Used for fixed queries with no user input.
- `PreparedStatement` — execute a SQL template with `?` placeholders. Used for anything with parameters.

```java
// Statement: SQL is hardcoded
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM users");

// PreparedStatement: parameters bound separately
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
ps.setString(1, userEmail);
ResultSet rs = ps.executeQuery();
```

**Always prefer `PreparedStatement` for anything with values.** It's safer (prevents SQL injection — topic 14) and often faster (the database can cache the compiled plan). Use plain `Statement` only for DDL or hardcoded admin queries.

### The execute methods

| Method | When | Returns |
|--------|------|---------|
| `executeQuery(sql)` | For `SELECT` | `ResultSet` |
| `executeUpdate(sql)` | For `INSERT`, `UPDATE`, `DELETE`, DDL | `int` (rows affected) |
| `execute(sql)` | Generic, when you don't know which kind | `boolean` (true if a `ResultSet`) |

90% of the time you'll use `executeQuery` or `executeUpdate` and know which one.

---

## ResultSet: the rows that come back

`ResultSet` is a cursor over the rows your query returned. It starts positioned *before* the first row. You call `next()` to advance one row at a time:

```java
ResultSet rs = stmt.executeQuery("SELECT id, name FROM users");
while (rs.next()) {
    int id = rs.getInt("id");           // by column name
    String name = rs.getString(2);      // or by 1-based column index
    System.out.println(id + " " + name);
}
```

**Column indexes start at 1, not 0.** This catches every JDBC newcomer.

**The cursor is forward-only by default.** You can configure scrollable ResultSets, but rarely need to.

**ResultSets must be closed** too, like Connections. The database holds resources for them.

---

## The standard JDBC dance

Almost every JDBC operation looks like this:

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement(SQL)) {
    ps.setString(1, someValue);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            // process the row
        }
    }
}
// All three resources auto-close in reverse order.
```

`try-with-resources` is non-negotiable in JDBC code. Without it, every exception path is a potential leak. Topic 13 drills this in.

---

## Why JDBC sometimes feels verbose

JDBC was designed in 1997. It exposes everything (every connection, every statement, every result row) by hand — no magic, no conventions. The verbosity is the price of being a *raw* layer.

Higher-level libraries (Spring `JdbcTemplate`, MyBatis, Hibernate, jOOQ) build on top of JDBC to hide some ceremony. But knowing the layer below lets you debug them when they break. **Every "ORM mystery" eventually becomes a JDBC reality check.**

---

## Code examples

1. `ConnectAndPing.java` — open a connection, run `SELECT 1`, close. The "hello world" of JDBC.
2. `StatementVsPreparedStatement.java` — same query both ways, with notes on when to use each.
3. `ResultSetWalkthrough.java` — read rows, access columns by name and index, look at metadata.
4. `ExecuteVariations.java` — `executeQuery` vs `executeUpdate` vs `execute`.

---

## Try this yourself

1. Run `ConnectAndPing.java` and confirm "connected" prints. This is the smoke test for your `SETUP.md` work.
2. In `ResultSetWalkthrough.java`, change `rs.getString("name")` to `rs.getString("nmae")` (typo). What exception type do you get?
3. In `StatementVsPreparedStatement.java`, try setting parameter index 0 instead of 1. What happens?

---

## Self-check

1. Why is `DriverManager.getConnection(...)` "expensive" in production code? What pattern solves it?
2. When would you use `Statement` instead of `PreparedStatement`?
3. What's special about index 1 in JDBC? Why does this trip people up?
