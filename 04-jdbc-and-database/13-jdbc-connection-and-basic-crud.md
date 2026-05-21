# 13 — JDBC Connection and Basic CRUD

Time to write CRUD against a database from Java properly. We'll cover each operation, with `PreparedStatement` from the start, and the resource-management discipline that keeps your app from leaking connections.

This is the topic where "I know SQL" and "I know how to talk to a database from Java" meet.

---

## The 4-step JDBC lifecycle

Every JDBC operation has the same shape:

1. **Open a Connection.** `DriverManager.getConnection(url, user, pass)`
2. **Create a Statement.** `conn.prepareStatement(sql)` or `conn.createStatement()`
3. **Execute.** `ps.executeQuery()`, `ps.executeUpdate()`
4. **Close everything.** `rs.close()`, `ps.close()`, `conn.close()` — in reverse.

Skipping step 4 is the most common JDBC bug in the wild. We'll address it head-on.

---

## CREATE (INSERT)

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO users (name, email) VALUES (?, ?)")) {
    ps.setString(1, "Alice");
    ps.setString(2, "alice@example.com");
    int inserted = ps.executeUpdate();
    System.out.println("rows inserted: " + inserted);
}
```

`executeUpdate()` returns the number of rows the statement affected. For a single-row insert, that's 1. **Check it** — if you got 0, the insert silently failed (rare but possible with conditional inserts), and silent failures cause bugs.

### Getting back an auto-generated ID

When the table has an `AUTO_INCREMENT` primary key, you usually want to know what ID was assigned. You ask for it explicitly:

```java
try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO users (name) VALUES (?)",
        Statement.RETURN_GENERATED_KEYS)) {
    ps.setString(1, "Alice");
    ps.executeUpdate();
    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (keys.next()) {
            long newId = keys.getLong(1);
            System.out.println("New user id: " + newId);
        }
    }
}
```

This is the standard pattern for "create resource, return its server-side ID."

---

## READ (SELECT)

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement(
             "SELECT id, name, email FROM users WHERE country = ?")) {
    ps.setString(1, "USA");
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            User u = new User(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"));
            // do something with u
        }
    }
}
```

Notice the nested `try-with-resources`: the `ResultSet` is opened inside, so it closes before the `PreparedStatement` closes — the right order.

### Handling NULL columns

Primitive `getInt()` returns `0` for SQL NULL, which can hide bugs. Use `wasNull()` or wrapper-aware methods:

```java
int age = rs.getInt("age");
if (rs.wasNull()) {
    // age was actually NULL; the 0 is meaningless
}

// Or:
Object ageObj = rs.getObject("age");
Integer age = (ageObj == null) ? null : (Integer) ageObj;
```

---

## UPDATE

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement(
             "UPDATE users SET email = ? WHERE id = ?")) {
    ps.setString(1, "newalice@example.com");
    ps.setLong(2, 1L);
    int updated = ps.executeUpdate();
    if (updated == 0) {
        // No row had id=1. Probably a bug. Don't silently ignore.
    }
}
```

Same shape as INSERT. The returned int tells you how many rows the WHERE clause matched.

---

## DELETE

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement(
             "DELETE FROM users WHERE id = ?")) {
    ps.setLong(1, 1L);
    int deleted = ps.executeUpdate();
}
```

By now this pattern is muscle memory.

---

## try-with-resources: non-negotiable

Without try-with-resources, JDBC code looks like:

```java
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = DriverManager.getConnection(URL, USER, PASS);
    ps = conn.prepareStatement("SELECT ...");
    rs = ps.executeQuery();
    while (rs.next()) { ... }
} catch (SQLException e) {
    // log
} finally {
    if (rs != null) try { rs.close(); } catch (SQLException ignore) {}
    if (ps != null) try { ps.close(); } catch (SQLException ignore) {}
    if (conn != null) try { conn.close(); } catch (SQLException ignore) {}
}
```

Ugly. Easy to forget one. Easy to swap the order. Each `close()` could throw, so you wrap each in another try. **People used to write this for a living.**

Try-with-resources (Java 7+) makes it:

```java
try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
     PreparedStatement ps = conn.prepareStatement("SELECT ...");
     ResultSet rs = ps.executeQuery()) {
    while (rs.next()) { ... }
}
// Everything closed automatically, in reverse declaration order,
// even if an exception was thrown.
```

**Rule: every `Connection`, `Statement`, `PreparedStatement`, and `ResultSet` must be in a try-with-resources.** No exceptions.

---

## What "leaking" actually means

A "connection leak" means the application opened a connection and never closed it. The connection sits open on the database side, occupying a slot.

Database servers have a limit on simultaneous connections (often 100–1000). Once that limit is hit, every new connection request hangs or fails with "too many connections" — the app appears completely broken even though "the code works fine."

**The symptoms in production:**

- App runs fine for an hour, then starts erroring with "Cannot get connection."
- Restart fixes it temporarily.
- Hours later, same problem.

That's a connection leak. The cause is *always* a code path that opened a connection without closing it — usually missing try-with-resources, sometimes an exception path that skipped the close.

This is why we hammer the resource discipline now. Before we get to connection pools (topic 17), you should have the leak-prevention reflexes built in.

---

## Reading ResultSets into your own objects

The common pattern: read each row into a domain object, return a list.

```java
record User(long id, String name, String email) {}

List<User> findAllUsers() throws SQLException {
    List<User> result = new ArrayList<>();
    String sql = "SELECT id, name, email FROM users";
    try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            result.add(new User(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email")));
        }
    }
    return result;
}
```

This is the building block of every DAO (Data Access Object) pattern you'll see. Frameworks like Spring `JdbcTemplate` and Hibernate just hide this for you.

---

## Code examples

1. `Insert.java` — INSERT with PreparedStatement, retrieve generated key.
2. `SelectAndRead.java` — SELECT with parameter binding, map rows to Java records.
3. `Update.java` — UPDATE, with row-count verification.
4. `Delete.java` — DELETE, with row-count verification.
5. `TryWithResourcesProper.java` — full CRUD lifecycle with proper resource management.
6. `ResourceLeakBroken.java` — what bad code looks like, and a demonstration that connections do leak.

---

## Try this yourself

1. In `SelectAndRead.java`, change one column name in the SQL to something invalid. What exception do you get, and at what line?
2. Modify `Insert.java` to insert two rows in sequence using the same `PreparedStatement`. Does the connection stay open between them?
3. Run `ResourceLeakBroken.java` in a loop (1000 iterations). Does it eventually fail?

---

## Self-check

1. What does `executeUpdate()` return, and what should you do with the value?
2. Why is `try-with-resources` essential for JDBC code, not just nice-to-have?
3. You're observing "Cannot get connection" errors after the app runs for ~2 hours. Where in the code do you look first?
