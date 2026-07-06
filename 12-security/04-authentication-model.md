# The authentication model — UserDetails, UserDetailsService, PasswordEncoder

The generated password from topic 02 got you in the door, but it's one throwaway user that changes on restart. Real apps have real users, stored somewhere, with hashed passwords. This topic is the four pieces Spring Security uses to answer "who is this caller?" — `Authentication`, `UserDetails`, `UserDetailsService`, and `PasswordEncoder` — and how they fit together so you can move from the generated user to users in a database.

---

## The problem this solves

Authentication needs to answer two questions when a request arrives with a username and password:

1. **Is there a user by this name?** — a lookup, wherever your users live (a table, an LDAP directory, a JSON file).
2. **Does the supplied password match the stored one?** — a comparison that must *never* be a plain `equals`, because you must never store the raw password to compare against.

Spring Security splits these into separate, replaceable pieces so you can change *where users live* without touching *how passwords are checked*, and vice versa. You implement the lookup; the framework owns the comparison and the plumbing.

---

## How it works — the four abstractions

| Piece | What it is | Who provides it |
|-------|-----------|-----------------|
| `Authentication` | The *result* of authentication: the principal (who), their authorities (what they can do), and an `isAuthenticated` flag. Stored in the `SecurityContext` for the rest of the request. | Spring Security builds it |
| `UserDetails` | What Spring Security needs to *know* about a user: username, the stored (hashed) password, authorities, and account-status flags (enabled, locked, expired). | You return it from the lookup |
| `UserDetailsService` | A one-method interface: `loadUserByUsername(String) → UserDetails`. **The lookup.** This is where "our users live in a database" is expressed. | **You implement it** |
| `PasswordEncoder` | One-way hashing: `encode(raw)` to store, `matches(raw, stored)` to verify. Never reversible. | You pick the algorithm (BCrypt) |

The component that ties the lookup and the comparison together is the **`DaoAuthenticationProvider`**. You don't usually write it — Spring auto-configures one as soon as it finds a `UserDetailsService` bean and a `PasswordEncoder` bean in the context.

### The handshake, step by step

When `curl -u alice:password` hits a protected endpoint:

```
Basic "alice:password" ─▶ BasicAuthenticationFilter
                            └▶ AuthenticationManager
                                 └▶ DaoAuthenticationProvider
                                      ├─ userDetailsService.loadUserByUsername("alice")
                                      │        └▶ UserDetails{ "alice", "$2a$10$…hash…", [ROLE_USER] }
                                      └─ passwordEncoder.matches("password", "$2a$10$…hash…")  ─▶ true
                                 ◀── Authentication{ principal=alice, authorities=[ROLE_USER], authenticated=true }
                            store in SecurityContext ─▶ request continues to BookController
```

The critical detail: **your `UserDetailsService` returns the stored hash, and the `DaoAuthenticationProvider` does the `matches` itself.** Your code never sees the raw password and never compares passwords. That separation is what keeps the comparison correct (constant-time, right algorithm) no matter where your users come from.

### Implementing the lookup against a database

The demo stores users in an `AppUser` JPA entity and looks them up with a repository. `JpaUserDetailsService` is the whole bridge:

```java
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public JpaUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("no user: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())   // the BCrypt hash from the DB
                .authorities(user.getRole())
                .build();
    }
}
```

Note `AppUser` (our domain/persistence object) is deliberately **separate** from `UserDetails` (the framework's contract). The service translates one into the other. Keep them separate: your `AppUser` can grow columns (email, created-at, failed-login-count) without any of that leaking into the security layer.

### Storing the password: encode, never save raw

The `PasswordEncoder` bean is used in two places, and it must be the *same* bean:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

- When we **create** a user (the seeder in `App`), we `encoder.encode("password")` and store the result — a `$2a$10$…` hash.
- When someone **logs in**, the `DaoAuthenticationProvider` calls `encoder.matches(submitted, storedHash)`.

If those used different algorithms, a correct password would never match its stored hash. (The *why* of BCrypt specifically — salting, work factor, why plain SHA-256 isn't enough — is topic 05. Here it's just "the right tool, used correctly.")

### The generated user is gone

The moment you define a `UserDetailsService` bean, Spring Boot's `UserDetailsServiceAutoConfiguration` backs off — no more "Using generated security password" line, no default `user`. Your database is now the single source of truth for who can log in. That's the graduation from topic 02.

---

## When to use it / when not to

- **A custom `UserDetailsService` is the standard way** to back authentication with your own user store (SQL, NoSQL, an internal service). Almost every real app has one.
- **In-memory users** (`InMemoryUserDetailsManager`) are fine for tests, demos, and tiny internal tools with a fixed user list. Don't use them for a real user base — you can't add users without a redeploy.
- **For token-based auth (JWT, OAuth2)** — topics 08–10 — the user often isn't loaded from *your* database at all; identity comes from a validated token. `UserDetailsService` is the model for the *username + password* case specifically.

---

## Common pitfalls

- **Storing the raw password and comparing with `equals`.** Then a database leak hands out every password directly. Always `encode` on write; let the provider `matches` on read. (Topic 05 shows the damage in full.)
- **No `PasswordEncoder` bean.** Spring Security's default encoder expects hashes prefixed like `{bcrypt}…`; a bare hash (or plaintext) then fails to match with a confusing "There is no PasswordEncoder mapped for the id 'null'" error. Define one `BCryptPasswordEncoder` bean and encode with it.
- **Leaking which usernames exist.** It's tempting to return a 404 "no such user" and a 401 "wrong password." That lets an attacker enumerate valid usernames. `DaoAuthenticationProvider` deliberately converts `UsernameNotFoundException` into the same `BadCredentialsException` as a wrong password (both → 401) — don't undo that by handling the two differently.
- **Coupling `AppUser` to `UserDetails`** (e.g. making the entity implement `UserDetails`). It works, but it drags JPA annotations and security concerns into one class and makes both harder to change. Prefer a small translating service.
- **Forgetting authorities, then being surprised at topic 07.** A user with no authorities authenticates fine but is denied every role-based rule. Give each user at least one authority (here, `ROLE_USER`).

---

## Try this yourself

The demo project is `CODE_EXAMPLES/04-authentication-model/authentication-model-demo`.

1. Run it and confirm the four `curl` results in the demo README. Note there is no generated-password line in the log anymore.
2. Add a third user in `App.seedUsers` (`carol` / your choice). Restart, and authenticate as `carol`. You added a user without touching any security config — that's the point of the `UserDetailsService` seam.
3. In `JpaUserDetailsService`, temporarily change `.password(user.getPassword())` to `.password("password")` (a raw, unencoded value) and try to log in as `alice`. It fails — the provider BCrypt-`matches` "password" against the literal string "password", which isn't a valid hash. Revert it. This is why the stored value must be an *encoded* hash.
4. Log in as `nobody` and as `alice` with a wrong password. Both give `401`. Confirm from the logs that internally both became `BadCredentialsException` — the app refuses to tell the caller which one was wrong.

## Code examples (reading order)

1. **`App.java`** — bootstrap, plus the seeder that `encode`s passwords and saves two users.
2. **`AppUser.java`** — the JPA entity: username, BCrypt-hash password, role.
3. **`AppUserRepository.java`** — `findByUsername`, the lookup the service needs.
4. **`JpaUserDetailsService.java`** — the bridge: DB row → `UserDetails`. The heart of the topic.
5. **`SecurityConfig.java`** — the `SecurityFilterChain` plus the `PasswordEncoder` bean.
6. **`BookController.java`** — the protected resource (unchanged from topic 03).
7. **`AuthenticationModelTest.java`** — anonymous 401, valid DB user 200, wrong password 401, unknown user 401, and "password is stored as a hash."

## Self-check

1. Name the four pieces (`Authentication`, `UserDetails`, `UserDetailsService`, `PasswordEncoder`) and say, in one line each, which question each one answers.
2. In the login handshake, which component actually compares the submitted password to the stored one — and why is it important that your `UserDetailsService` does *not* do that comparison?
3. Why did the "Using generated security password" line disappear once you added `JpaUserDetailsService`?
4. Why does the app return the same `401` for "unknown user" and "wrong password," and what attack does that prevent?
5. Why keep `AppUser` separate from Spring Security's `UserDetails` instead of having the entity implement `UserDetails` directly?
