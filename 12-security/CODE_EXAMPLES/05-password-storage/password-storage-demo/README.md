# 05 — Password storage (vulnerable → safe)

Three runnable demos that let you *watch* the failure modes and the fix. Storing plaintext, storing an unsalted fast hash (SHA-256), and storing a BCrypt hash.

## Run the demos

```bash
mvn -q exec:java -Dexec.mainClass=com.example.PlaintextStoreVulnerable
mvn -q exec:java -Dexec.mainClass=com.example.FastHashVulnerable
mvn -q exec:java -Dexec.mainClass=com.example.BcryptSafe
```

What you'll see:

- **`PlaintextStoreVulnerable`** — the "database" is just everyone's passwords in the clear. `alice` and `carol` visibly share one.
- **`FastHashVulnerable`** — SHA-256 hashes. `alice` and `carol` still have *identical* hashes (no salt), and a small dictionary cracks the entire table in ~1 ms.
- **`BcryptSafe`** — encoding `"correcthorse"` twice gives two *different* hashes (per-hash random salt), `matches()` still verifies, and hashing is hundreds of times slower than SHA-256 by design.

## Run the tests

```bash
mvn -q test
```

`PasswordStorageTest` asserts the properties the demos narrate: the unsalted hash is deterministic (leaks shared passwords) and dictionary-crackable; BCrypt is salted (same input → different hashes), still verifiable, and formatted `$2a$10$…`.

> The BCrypt cost factor (default 10) is what makes it slow. On your machine each hash takes a few milliseconds — fine for a login, painful for an attacker doing billions of guesses.
