# 00 — Why Databases Exist

Before learning SQL or JDBC, get clear on the problem databases solve. Otherwise you'll memorize syntax without knowing why any of it matters.

This topic has no code. It's a thought experiment that motivates everything in the next 19 topics.

---

## A thought experiment: a todo app without a database

You're building a todo app. Five users. Each todo has an id, a title, and a "done" flag. You decide databases are overkill — you'll just save todos to a plain text file, `todos.txt`:

```
1|Buy milk|false
2|Call mom|true
3|Finish module 04|false
```

When the app starts, it reads the file. When a user adds a todo, the app appends a line. When a user marks one done, the app rewrites the file with the updated flag.

Five users, low traffic, it works.

Now think about what happens as the app grows.

### Problem 1: concurrent writes corrupt the file

User A clicks "add todo" at the exact moment User B clicks "mark done." Both processes read the file, both write it back. One write overwrites the other. **One user's change vanishes silently.** No error, no warning.

In database terms: there was no *transaction* and no *isolation*.

### Problem 2: finding things gets slow

You have 50,000 todos and need to find every incomplete one assigned to user 42. With a text file, you read the entire file from disk and scan every line. **Linear time, every query, forever.**

In database terms: there's no *index*.

### Problem 3: there's no schema

User C's app version has a bug and writes `false|Buy milk|3` (columns reversed). The file silently accepts it. Next time the app reads the file, it crashes — or worse, marks "Buy milk" as a user ID. **Nothing rejected the malformed row.**

In database terms: there are no *constraints* and no *typed columns*.

### Problem 4: relationships are nightmares

Each todo belongs to a user, each user belongs to a team, each team has many projects. With files: `users.txt`, `teams.txt`, `projects.txt`, `todos.txt`. To answer "show all overdue todos for projects in Marketing," you read four files, build hashmaps in memory, join them by hand. Every. Single. Query.

In database terms: there's no *JOIN*, no *foreign keys*, no *relational integrity*.

### Problem 5: when the server crashes, you lose data

The app appends a line, the server crashes mid-write, the file is now half-written and unreadable. You lose every todo on disk.

In database terms: there's no *durability*, no *write-ahead log*, no *recovery*.

---

## What a database is, in one sentence

A database is a program whose entire job is to solve those five problems for you so your application doesn't have to.

That's it. Everything else — SQL, indexes, transactions, ACID, joins — is mechanism. The five problems are the *purpose*.

---

## What "persistence" means

When the app stops, what's in RAM disappears. What's been written to disk (or sent to a database that wrote it to disk) survives. **Persistent data outlives the process that created it.**

Every backend app needs persistence. The choice isn't *whether* to persist — it's *how*. The "how" is almost always "a database," for the five reasons above.

---

## Why relational databases specifically

Two main families exist:

| Family | Examples | Best for |
|--------|----------|----------|
| Relational (SQL) | PostgreSQL, MySQL, SQLite, H2, Oracle | Most business apps. Strong consistency, well-defined relationships, mature tooling. |
| NoSQL | MongoDB, Redis, Cassandra, DynamoDB | Specific shapes of data — document stores, caches, very high write throughput, etc. |

This module teaches **relational** databases because:

1. ~80% of backend systems use them. They're the default.
2. Spring Data JPA, Hibernate, JDBC — all the Java backend tooling assumes relational by default.
3. Once you understand relational thinking, NoSQL is a smaller jump. The reverse is harder.

NoSQL isn't covered in this roadmap. Learn relational deeply first.

---

## Self-check

Before moving on, answer these aloud in your own words. If you can't, re-read the relevant section.

1. Two users hit "save" at the exact same moment on a file-backed app. What's the failure mode and what's it called in database vocabulary?
2. Why is a JOIN inside a database faster than the equivalent "read both files into memory and combine in Java"?
3. If "persistence" just means "writes to disk," why isn't writing to a text file good enough for a backend?
