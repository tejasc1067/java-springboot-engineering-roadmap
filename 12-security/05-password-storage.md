# Password storage — why BCrypt, shown by breaking the alternatives

Topic 04 stored passwords as BCrypt hashes and told you that was "the right tool, used correctly." This topic earns that claim by breaking the wrong tools. You'll run code that dumps a plaintext store, cracks an unsalted SHA-256 store from a wordlist in milliseconds, and then see BCrypt defeat both attacks — with a random salt and a deliberately slow work factor. The lesson isn't "use BCrypt because we said so"; it's "watch what happens when you don't."

---

## The problem this solves

Your database *will* leak someday — a stolen backup, a SQL-injection dump, an over-broad query in a log, a misconfigured cloud bucket. Password storage is designed around that assumption: **the goal is that a full database dump still doesn't hand the attacker usable passwords.** Everything below is judged by that one question: given the leaked table, how fast can an attacker recover the real passwords?

There's a second reason this matters beyond your own site: people reuse passwords. A password you leak in plaintext is often the same one protecting that user's email and bank. Your breach becomes theirs.

---

## Vulnerable version 1 — plaintext (`PlaintextStoreVulnerable`)

Store the password as typed. Run the demo and the "database" is simply:

```
alice -> correcthorse
bob   -> hunter2
carol -> correcthorse
```

The dump *is* the breach — every password is immediately usable, and you can see at a glance that `alice` and `carol` share one. There is no attack to perform; you already lost. Nobody defends this on purpose, but it still happens via "temporary" debug columns, plaintext logs of login requests, and password-reset emails that echo the password back.

## Vulnerable version 2 — unsalted fast hash (`FastHashVulnerable`)

The common "we're not that naive" move: hash with SHA-256 and store the hash. Run the demo and two problems show up immediately.

**Problem 1 — no salt.** SHA-256 is deterministic: the same input always gives the same output. So `alice` and `carol`, who share a password, have byte-for-byte *identical* hashes:

```
alice -> 6ea09c25…343990bd
carol -> 6ea09c25…343990bd      # identical — the dump still reveals they share a password
```

Determinism also means an attacker can precompute hashes of common passwords once (a "rainbow table") and match them against *every* leaked database ever. Hashing alone bought almost nothing.

**Problem 2 — too fast.** SHA-256 is engineered to be *fast* — that's a virtue for checksums and a disaster for passwords. The demo runs a tiny wordlist against the dump:

```
cracked alice = "correcthorse"
cracked bob   = "hunter2"
cracked carol = "correcthorse"
Cracked the entire table in 1 ms.
```

A real attacker uses a GPU doing **billions** of SHA-256 guesses per second and wordlists of hundreds of millions of entries. Any password that isn't long and random falls in seconds. MD5 and SHA-1 are worse (also broken); SHA-256/512 are fine hashes for the *wrong job*.

## The fix — BCrypt (`BcryptSafe`)

BCrypt is a *password hashing* function. It fixes both problems by design.

**It salts, automatically.** BCrypt generates a random salt per call and stores it *inside* the hash. So encoding the same password twice gives two different results:

```
encode("correcthorse") -> $2a$10$OJF2c.g7Lo3qYlcXb9QzMO/6llwzhCmz4woD…
encode("correcthorse") -> $2a$10$48JkXF.o9DmEJbTgn23OEuNIAELiPhLUZDbi…   # different!
```

Now `alice` and `carol` look unrelated in the dump, and precomputed rainbow tables are useless — the attacker must attack each hash separately, salt included. The format is `$2a$<cost>$<22-char-salt><31-char-hash>`; because the salt travels with the hash, verification needs nothing else.

**It's deliberately slow, and tunable.** The `<cost>` (work factor, default 10) sets how many rounds BCrypt runs — each `+1` doubles the work. The demo times it:

```
Hashing 50 passwords: BCrypt = ~2600 ms, SHA-256 = ~3 ms  (hundreds of times slower)
```

A few milliseconds per login is invisible to your users. But it turns an attacker's *billions of guesses per second* into *thousands* — the dictionary attack that finished in 1 ms against SHA-256 now takes years. As hardware gets faster, you raise the cost factor to keep pace. That's the knob fast hashes don't have.

**Verification still works** because the provider re-derives the hash from the submitted password + the stored salt and compares:

```java
encoder.matches("correcthorse", storedHash);  // true
encoder.matches("wrong", storedHash);          // false
```

This is exactly the `matches` call the `DaoAuthenticationProvider` made for you in topic 04 — now you know why the stored value looks like `$2a$10$…` and why two users with the same password don't collide.

---

## When to use what

- **Passwords → BCrypt** (or Argon2 / scrypt / PBKDF2). Spring Security ships `BCryptPasswordEncoder`; it's the sane default. Argon2 (`Argon2PasswordEncoder`) is the newer, memory-hard choice if you want it.
- **`DelegatingPasswordEncoder`** (what `PasswordEncoderFactories.createDelegatingPasswordEncoder()` returns) stores a `{bcrypt}`-style prefix so you can *migrate* algorithms over time without invalidating old hashes. Reach for it when you expect to change encoders later.
- **Fast hashes (SHA-256, SHA-3) → integrity, not passwords.** File checksums, HMAC signatures, content addressing. Perfect there, wrong for passwords.
- **Never** encryption for passwords. Encryption is reversible — if you can decrypt to compare, so can whoever steals the key. Passwords want a one-way *hash*, not a cipher.

---

## Common pitfalls

- **"We hash, so we're safe."** Only if it's a *password* hash (BCrypt/Argon2/scrypt/PBKDF2). An unsalted fast hash is barely better than plaintext against a real attacker, as the demo shows.
- **Rolling your own salting on top of a fast hash.** `sha256(salt + password)` fixes the rainbow-table problem but not the *speed* problem — a GPU still tries billions per second. Use a function that's slow by design.
- **Setting the cost factor too low (or too high).** Too low (4–6) is nearly as crackable as unsalted; absurdly high stalls logins and becomes a denial-of-service vector against yourself. Default 10–12 is the usual range; measure on your hardware.
- **Logging the raw password.** All the hashing in the world is undone by one `log.info("login attempt: " + request)` that serializes the password into your logs. Never log credentials.
- **Encrypting instead of hashing.** If your "hashed" passwords can be turned back into plaintext with a key, they're not hashed. Whoever gets the key gets everything.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/05-password-storage/password-storage-demo`.

1. Run all three mains (see the demo README). Watch `FastHashVulnerable` crack the table in ~1 ms, then watch `BcryptSafe` produce two different hashes for the same password.
2. Add your own password to `FastHashVulnerable`'s dictionary and confirm it gets cracked too — then remove a word and watch that user survive (the attack only finds passwords in the list; strong random passwords aren't in any list).
3. In `BcryptSafe`, change `new BCryptPasswordEncoder()` to `new BCryptPasswordEncoder(12)` and re-run. Note the hash prefix becomes `$2a$12$` and the timing roughly quadruples (two doublings). That's the cost knob.
4. Take a `$2a$10$…` hash printed by `BcryptSafe` and paste it into a fresh `matches("correcthorse", theHash)` call. It returns `true` even though you never stored the salt separately — because the salt is inside the hash string.

## Code examples (reading order)

1. **`PlaintextStoreVulnerable.java`** — the dump is the breach. The baseline failure.
2. **`FastHashVulnerable.java`** — SHA-256: identical hashes for shared passwords, and a dictionary crack in milliseconds.
3. **`BcryptSafe.java`** — BCrypt: per-hash salt, working verification, and the deliberate slowness that defeats the attack.
4. **`PasswordStorageTest.java`** — asserts each property: fast-hash determinism, the successful dictionary crack, BCrypt salting, verification, and hash format.

## Self-check

1. A database of SHA-256-hashed passwords leaks. Why is that only marginally better than a plaintext leak against a serious attacker? Name the two distinct weaknesses.
2. Encoding the same password with BCrypt twice gives two different strings. How does verification still work later, with only the stored hash?
3. What does the BCrypt "cost" / work factor control, and what breaks if you set it far too low — or far too high?
4. Your teammate proposes `sha256(salt + password)` with a random per-user salt. Which of SHA-256's two problems does that fix, and which does it leave wide open?
5. Why is *encryption* the wrong choice for storing passwords, even with a strong cipher?
