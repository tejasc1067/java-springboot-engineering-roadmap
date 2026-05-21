# 14 — PreparedStatement and SQL Injection

This is the most important topic in this module by impact. Get it wrong and you ship security holes. Get it right and an entire class of vulnerabilities can't exist in your code.

The fix is *easier* than the broken pattern. There's no excuse for getting this wrong.

---

## The vulnerability

A login form. The user types an email and a password. The naïve backend builds the query like this:

```java
String email = request.getParameter("email");
String password = request.getParameter("password");

String sql = "SELECT id FROM users " +
             "WHERE email = '" + email + "' " +
             "AND password = '" + password + "'";

ResultSet rs = stmt.executeQuery(sql);
if (rs.next()) {
    // valid login!
}
```

For a normal user typing `alice@example.com` / `secret123`, the SQL is:

```sql
SELECT id FROM users WHERE email = 'alice@example.com' AND password = 'secret123'
```

Fine. Now an attacker types `email = anything' OR '1'='1` and any password. The SQL becomes:

```sql
SELECT id FROM users WHERE email = 'anything' OR '1'='1' AND password = '...'
```

`'1'='1'` is always true. Combined with `OR`, the WHERE clause matches **every user in the table**. The query returns the first one. **The attacker is now logged in as someone — usually the first user in the database, often the admin.**

That's SQL injection. The fix is simple. Let's see it.

---

## The fix: PreparedStatement with parameters

```java
String sql = "SELECT id FROM users WHERE email = ? AND password = ?";

try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, email);
    ps.setString(2, password);
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            // valid login
        }
    }
}
```

Now if the attacker submits the same evil email `anything' OR '1'='1`, here's what happens:

1. The SQL string sent to the database is *literally* `SELECT id FROM users WHERE email = ? AND password = ?`. Untouched.
2. The parameters are sent **separately** as data, not as SQL.
3. The database treats the attacker's string as the *literal value to match against*. It looks for a user whose email is the string `anything' OR '1'='1`. No such user exists. Login fails.

The attack is *structurally impossible* with PreparedStatement. There's no string concatenation for an attacker to break out of.

---

## Why this works (the protocol-level reason)

When you `executeUpdate` a plain `Statement`, the entire SQL text is sent to the database, which parses it into a plan. Any quote-breaking trickery in the input becomes part of the SQL.

When you `executeQuery` a `PreparedStatement`, two things happen:

1. The SQL template (with `?` placeholders) is sent and **parsed first**. The plan is fixed.
2. Parameters are sent separately, as typed values. They never get parsed as SQL; they only get *bound* to the placeholders in the existing plan.

The parser never sees the attacker's data, so the attacker has nothing to inject into. Injection is impossible at the protocol level.

---

## What if I "escape" the input myself?

You can't. Or rather, you almost certainly will get it wrong.

People hand-write escaping (replace `'` with `''`, strip semicolons, etc.) thinking they're safe. They aren't:

- Different databases escape differently.
- Different *contexts* within SQL escape differently (string vs. identifier vs. comment).
- Unicode and encoding tricks bypass naïve escapers.
- "Looks safe" lists like "strip semicolons" miss every modern attack vector.

**The only correct fix is PreparedStatement.** Don't hand-roll escaping. The database driver knows how to handle every type for every database it ships with. Trust it.

---

## When parameters can't help: dynamic columns/tables

`?` placeholders only bind **values** — strings, numbers, dates. They cannot bind **identifiers** (column names, table names, ORDER BY direction).

```java
// This DOES NOT work — `?` cannot be a column name.
String sql = "SELECT * FROM users ORDER BY ?";
ps.setString(1, "email");
// Error: invalid SQL.
```

If you genuinely need a dynamic identifier (e.g., user picks which column to sort by), you can't use a parameter. So you have to validate against an allow-list:

```java
String userInput = request.getParameter("sortBy");
Set<String> allowed = Set.of("id", "name", "email", "created_at");
if (!allowed.contains(userInput)) {
    throw new IllegalArgumentException("invalid sort column");
}
String sql = "SELECT * FROM users ORDER BY " + userInput;
```

The allow-list ensures only known-safe strings reach the SQL. **This is the only acceptable string-concatenation pattern in SQL.** And even here you should treat it nervously — try to design APIs so users pick sort options from a known list (e.g. an enum), not arbitrary strings.

---

## Other places injection hides

SQL injection isn't only in login forms. Watch for:

- Search filters that build dynamic WHERE clauses
- Reporting tools that take user-typed criteria
- Admin tools that "let you run any SQL" — still vulnerable to higher-level abuse
- ORM string-template features (e.g. JPQL/HQL injection — yes, ORMs aren't automatically safe if you use their string-concatenation features)

The mental model: **anywhere user input becomes part of a query string, you have potential injection.** Anywhere it becomes a *parameter*, you don't.

---

## Performance bonus

PreparedStatement isn't only safer — it's often faster. The database parses and plans the query *once* when you prepare it. Each subsequent execution skips that work, just binding new parameters and running.

For a query you run thousands of times (typical in a web app), this is a real difference. The safer pattern is also the faster pattern.

---

## A defensive habit

When you write any SQL in Java, before you finish, ask:

> "Where does the data in this query come from?"

If any part of the query *string* (not parameter) comes from outside your code — request parameter, header, cookie, file, anything — stop and rewrite it as a parameterized PreparedStatement or validate against an allow-list. Make this a reflex.

---

## Code examples

1. `StringConcatVulnerable.java` — login form with string concatenation. Looks innocent.
2. `SqlInjectionExploit.java` — same code, attacker input, login bypass demonstrated.
3. `PreparedStatementSafe.java` — fixed. Same attacker input now fails (correctly).
4. `MultipleParameters.java` — binding strings, numbers, and dates together.
5. `DynamicSqlSafely.java` — when the column name itself is user-controlled. Allow-list pattern.

**Run them in order.** The injection example is real — you'll see it work. The fix is real — you'll see it not work. That contrast is the lesson.

---

## Try this yourself

1. In `SqlInjectionExploit.java`, try different attacker inputs. `'; DROP TABLE users; --` is the classic XKCD attack — does H2 allow stacked statements?
2. In `PreparedStatementSafe.java`, observe that the SAME attacker input that worked in 02 now returns "login failed."
3. In `DynamicSqlSafely.java`, remove the allow-list check. Construct a malicious input that bypasses the missing check.

---

## Self-check

1. In one sentence, why is `PreparedStatement` immune to SQL injection at a protocol level?
2. You need a query that lets the user choose the ORDER BY column. Why can't a `?` placeholder do this, and what's the safe alternative?
3. Your teammate wrote a query that "escapes the apostrophe" themselves and asks if it's safe now. What's your reply?
