# 04 — JDBC and Databases

How to talk to a relational database from Java, without a framework hiding things from you.

This module is the bridge between knowing Java and knowing backend persistence. By the end, you'll have written real `Connection`, `PreparedStatement`, and `ResultSet` code, broken it on purpose to see how it fails, and understand what frameworks like Spring Data JPA do for you when we get to them later.

## Who this is for

- Beginners who finished modules 01–03 and can read Java code.
- Career switchers who already code and want to understand the JVM's database layer before learning Hibernate or Spring Data.

## What you'll be able to do at the end

Concrete, observable outcomes. Check each one off as you go.

- [ ] Explain the four words "Connection", "Statement", "PreparedStatement", "ResultSet" and what each one does.
- [ ] Write a JDBC program that creates a table, inserts rows, queries them, and prints the result.
- [ ] Identify a SQL injection vulnerability in code review and show how to fix it.
- [ ] Explain why a transaction wraps multiple statements and what `rollback()` does on failure.
- [ ] Spot a connection leak in code and fix it with `try-with-resources`.
- [ ] Explain in one sentence why production apps use a connection pool like HikariCP.
- [ ] Read a simple `EXPLAIN` output and identify whether a query hits an index or does a full table scan.

If you can do all 7 at the end, the module worked.

## Prerequisites

Before starting, you should be able to:

- Read and write Java code (basic OOP, exceptions, collections) — module 01–03.
- Run a `.java` file from the command line or IntelliJ — see `SETUP.md` in the repo root.
- Understand that "the database" is a separate program your app talks to over a connection. You'll learn the details here.

You do **not** need to know SQL before starting. Topics 05–11 cover SQL from scratch.

## How this module is organized

Topics 00–04 are conceptual — what databases are, how they're structured. No JDBC yet, light or no code.

Topics 05–11 are SQL — running real queries against H2 from Java. You'll see the SQL and watch it execute.

Topics 12–18 are JDBC — the Java API for talking to relational databases. This is where the bugs (leaks, injections, broken transactions) get demonstrated and fixed.

Topic 19 is a revision/interview summary.

```text
00-database-foundation                            ← why databases exist
01-introduction-to-databases                      ← what databases actually are
02-relational-database-fundamentals               ← tables, rows, relationships
03-primary-key-foreign-key-and-constraints        ← integrity
04-database-design-and-normalization              ← how to design schemas
05-sql-introduction                               ← SQL begins
06-basic-sql-queries                              ← SELECT/INSERT/UPDATE/DELETE
07-where-group-by-having-order-by                 ← filtering, grouping
08-joins-deep-dive                                ← combining tables
09-subqueries-and-nested-queries
10-indexes-and-query-optimization                 ← performance basics
11-transactions-and-acid-properties               ← consistency
12-jdbc-introduction-and-architecture             ← JDBC begins
13-jdbc-connection-and-basic-crud                 ← real Java code now
14-preparedstatement-and-sql-injection            ← security
15-jdbc-transaction-management
16-jdbc-batch-processing
17-jdbc-connection-pooling                        ← HikariCP
18-jdbc-best-practices-and-production-considerations
19-jdbc-interview-revision
```

## How to run the code

See `SETUP.md` at the repo root. One-time: download a single H2 jar to `04-jdbc-and-database/CODE_EXAMPLES/lib/`. After that, every example runs with:

```bash
java --class-path "04-jdbc-and-database/CODE_EXAMPLES/lib/h2-2.2.224.jar" \
  04-jdbc-and-database/CODE_EXAMPLES/<topic>/<File>.java
```

Inside each topic's `CODE_EXAMPLES/` folder, files are named in PascalCase (no numeric prefix). The *reading order* lives in each topic markdown's "Code examples" section — that's the canonical sequence; alphabetical file order will not match.

- Files with `Vulnerable`, `Broken`, or `Leaky` in their names are intentionally bad. The next file listed in the markdown fixes them (e.g. `StringConcatVulnerable.java` → `PreparedStatementSafe.java`). Run both, in the markdown's listed order. The bad one is the lesson.

## Working through a topic

For each topic file:

1. Read the markdown.
2. Run the first code example listed. Read it. Run it again with a small change.
3. Run the next listed example. Compare.
4. Do the "Try this yourself" exercise at the end of the markdown.
5. Answer the "Self-check" questions out loud, in your own words.

If you can't answer all 3 self-check questions, re-read the topic. Don't move on.

## Why no Maven yet?

You'll need exactly one external library in this module (H2). We download it directly so you can focus on the persistence concepts without learning a build tool at the same time. **Module 05** covers Maven and Gradle properly — at that point you'll never download a jar by hand again.

## Module checkpoint

Before moving to module 05, you should be able to answer these aloud:

1. Walk through what happens when `DriverManager.getConnection(url, user, password)` returns. What did the JVM just do?
2. You're reviewing a teammate's PR. They wrote `"SELECT * FROM users WHERE email = '" + email + "'"`. What do you say in the review and why?
3. A user reports the app gets slow after running for a few hours, then throws `Cannot get connection`. What are the top two suspects?
4. Explain in one sentence what `connection.setAutoCommit(false)` changes.

If those four are easy, you're ready. If any feel shaky, the corresponding topic is `12`, `14`, `17`, and `15`.
