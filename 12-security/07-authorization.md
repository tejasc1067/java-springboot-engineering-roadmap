# Authorization — URL rules, method security, roles vs. authorities

Authentication (topics 04–06) answered "who are you?" This topic answers "what may you do?" — the second half of the `401`/`403` split from topic 01. You'll gate access two complementary ways: coarse **URL rules** in the filter chain (`/api/admin/**` needs `ADMIN`) and fine **method security** on individual methods (`@PreAuthorize`), and you'll see why `hasRole("ADMIN")` and the authority `ROLE_ADMIN` are the same thing wearing two names.

---

## The problem this solves

A logged-in user is not an all-access user. `alice` can read the catalog; only an admin can delete a book or view the internal stats. Once authentication has established *who* the caller is (and their authorities), authorization decides whether *this* caller may perform *this* action. When they may not, the answer is **`403 Forbidden`** — not `401`. `alice` is perfectly authenticated; logging in again won't help; she simply lacks the role.

You need this at two granularities: broad strokes by URL (an entire admin section), and precise rules on specific operations (this one delete method, or "only the owner of this resource").

---

## How it works

### Roles are authorities with a prefix

A user carries a set of **authorities** — plain strings like `ROLE_ADMIN` or `books:delete`. A **role** is just an authority that starts with `ROLE_`. Spring Security gives you two vocabularies:

- `hasAuthority("ROLE_ADMIN")` — checks the authority string *exactly*.
- `hasRole("ADMIN")` — a convenience that **prepends `ROLE_` for you**, then checks `ROLE_ADMIN`.

They test the same thing here. And when you build a user with `.roles("ADMIN")`, Spring stores the authority `ROLE_ADMIN`. So `roles("ADMIN")` ⇄ `hasRole("ADMIN")` ⇄ authority `ROLE_ADMIN`. Mixing the two vocabularies is the #1 source of "why is this always 403?" bugs (see pitfalls).

### Layer 1 — URL-based authorization (coarse, by path)

In the filter chain, `authorizeHttpRequests` matches request paths to access rules, top-to-bottom, first match wins (topic 03):

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/admin/**").hasRole("ADMIN")   // an entire section, one rule
    .anyRequest().authenticated())
```

The common rule methods: `permitAll()`, `authenticated()`, `hasRole("X")`, `hasAnyRole("X","Y")`, `hasAuthority("...")`, `denyAll()`. This is perfect for whole areas of the app (`/admin/**`, `/public/**`) but can't express "only the owner" or per-method rules — for that you go to layer 2.

### Layer 2 — method security (fine, by method)

Turn it on with `@EnableMethodSecurity`, then annotate methods:

```java
@DeleteMapping("/books/{id}")
@PreAuthorize("hasRole('ADMIN')")          // checked BEFORE the method runs
public void delete(@PathVariable Long id) { ... }
```

`@PreAuthorize` evaluates a **SpEL expression** before the method executes; if it's false, the method never runs and the caller gets `403`. The expression can use the same helpers (`hasRole`, `hasAuthority`), boolean logic, method arguments, and the `authentication` object:

```java
@PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
public Profile profile(@PathVariable String username) { ... }
```

That's the **ownership** pattern: an admin sees anyone's profile; a normal user sees only their own. `#username` binds the method parameter (Spring Boot compiles with `-parameters`, so the name is available); `authentication.name` is the logged-in user. There's also `@PostAuthorize` (checked *after* the method, so it can inspect the return value — e.g. `returnObject.owner == authentication.name`), used when you can't know the answer until you've loaded the object.

### The two layers complement each other

URL rules and method security aren't either/or. In the demo, `/api/books/**` is only `authenticated()` at the URL layer, but `delete` adds `@PreAuthorize("hasRole('ADMIN')")` on top. So `alice` passes the URL rule (she's authenticated) and is then stopped by the method rule (she's not admin) — `403`. Broad gate at the door, precise gate at the method.

### What the demo proves

| Request | `alice` (USER) | `admin` (ADMIN) | Enforced by |
|---------|----------------|-----------------|-------------|
| `GET /api/books` | 200 | 200 | URL: `authenticated()` |
| `GET /api/admin/stats` | **403** | 200 | URL: `hasRole('ADMIN')` |
| `DELETE /api/books/1` | **403** | 200 | method: `@PreAuthorize("hasRole('ADMIN')")` |
| `GET /api/users/alice/profile` | 200 | 200 | method: ownership expression |
| `GET /api/users/bob/profile` | **403** | 200 | method: ownership expression |
| `GET /api/books` (anonymous) | **401** | — | not authenticated at all |

---

## When to use which

- **URL rules** for broad, path-shaped boundaries: `/admin/**`, `/public/**`, "the whole API needs login." Easy to see the whole policy in one place.
- **Method security** for rules that are about *the operation or the data*: "only admins delete," "only the owner edits," "only during business hours." These don't map cleanly to a URL.
- **Both, together, in real apps.** A coarse URL net plus precise method annotations is the norm. Don't try to force per-object ownership into a URL rule, and don't scatter `@PreAuthorize` on every endpoint when one URL rule covers a whole section.

---

## Common pitfalls

- **Double-prefixing the role.** `hasRole("ROLE_ADMIN")` checks for `ROLE_ROLE_ADMIN`, which nobody has → always `403`. Use `hasRole("ADMIN")` *or* `hasAuthority("ROLE_ADMIN")`, never `hasRole("ROLE_ADMIN")`.
- **Forgetting `@EnableMethodSecurity`.** Without it, `@PreAuthorize` annotations are **silently ignored** — the method runs for everyone and you have a wide-open endpoint that *looks* protected. This is a dangerous, quiet failure. Turn it on, and test that a forbidden case actually returns `403`.
- **Self-invocation bypasses `@PreAuthorize`.** Method security works via proxies, so a call from *within the same bean* (`this.delete(...)`) skips the check. The annotation only fires when the method is called through the Spring proxy (i.e., from another bean). Put secured methods where they're called externally.
- **Confusing `401` and `403` in handlers.** A wrong-role user must get `403`, not `401`; sending `401` tells the client to re-authenticate, which will never fix a permissions problem. (Wiring these cleanly into your error contract is topic 12.)
- **Leaking existence via `403` vs `404`.** For sensitive resources, a `403` on `/api/users/bob/profile` confirms `bob` exists. Sometimes you deliberately return `404` instead to avoid revealing that — a product decision, but be aware the status code leaks information.
- **Relying only on URL rules for data ownership.** `/api/orders/{id}` with a URL rule can't know whether *this* order belongs to *this* user. That check has to be method/expression level (`@PostAuthorize` or a service check), or one user reads another's orders.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/07-authorization/authorization-demo`.

1. Run it and walk the `curl` table in the demo README. Watch `alice` get `403` on the admin URL and the delete, and `200` on her own profile but `403` on `bob`'s.
2. **Break it on purpose:** remove `@EnableMethodSecurity` from `SecurityConfig`, re-run the tests. The `@PreAuthorize` tests now *fail* because the annotation is ignored and `alice`'s delete succeeds — the silent-failure pitfall, made loud. Put it back.
3. Change `hasRole("ADMIN")` to `hasRole("ROLE_ADMIN")` on the delete method and re-run. Now even `admin` gets `403` (it's looking for `ROLE_ROLE_ADMIN`). Revert.
4. Add a `@PreAuthorize("hasRole('ADMIN')")` on the `all()` read method and confirm `alice` loses read access — then decide that's too strict and remove it. Feel the difference between a URL rule and a method rule.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record.
3. **`SecurityConfig.java`** — `@EnableMethodSecurity`, the URL rule for `/api/admin/**`, and the two roled users. Roles-vs-authorities lives here.
4. **`BookController.java`** — the three method-security styles: none (URL-protected), `@PreAuthorize` role, and `@PreAuthorize` ownership expression.
5. **`AuthorizationTest.java`** — 9 cases: each style for each role, plus anonymous → 401.

## Self-check

1. `alice` is logged in and calls a delete endpoint she's not allowed to use. What status code does she get, and why is it *not* `401`?
2. What does `hasRole("ADMIN")` actually check for, and what's wrong with writing `hasRole("ROLE_ADMIN")`?
3. When would you use a URL rule (`authorizeHttpRequests`) versus a method rule (`@PreAuthorize`)? Give one example that *needs* the method rule.
4. You add `@PreAuthorize` to a method but it seems to have no effect — everyone can call it. Name the two most likely causes.
5. Write the SpEL for "an admin, or the user who owns this resource, may proceed," given a `String username` parameter.
