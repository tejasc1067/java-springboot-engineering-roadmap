# Spring Security defaults — what one dependency locks down

Add `spring-boot-starter-security` to an app and, before you write a single line of security code, every endpoint stops being public. Requests with no credentials get `401`, a random password is generated at startup, and a filter chain now runs in front of your controllers. This topic makes you *see* that default — the locked endpoint, the generated password, the `WWW-Authenticate` header — because the rest of the module is a series of deliberate departures from it, and you can't depart from a default you haven't watched happen.

---

## The problem this solves

Topic 01 left the `Book` API wide open: anyone could read, write, or delete. The naive fix is to imagine writing your own filter — check a header on every request, compare a password, reject if wrong — and to get it right for every endpoint, including the ones you add next month. That is exactly the code people get subtly wrong (forgetting an endpoint, comparing passwords with `==`, leaking timing).

Spring Security's answer is to invert the default: the moment it's on the classpath, **nothing is open**. You don't remember to secure endpoints; you deliberately *unsecure* the ones that should be public (topic 03). Forgetting an endpoint now fails safe — it stays locked — instead of failing open.

---

## How it works

### Adding the starter is the entire change

The demo's `BookController` has no security annotations and no login code. It's the same controller you'd write in an app with no security at all. The only difference from an open app is one line in the `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

That pulls in Spring Security *and* triggers Spring Boot's `SecurityAutoConfiguration`, which — finding no `SecurityFilterChain` bean of your own — installs a **default** one. That default chain says, in effect:

- `authorizeHttpRequests` → **`anyRequest().authenticated()`** (every request needs a logged-in user),
- enable **HTTP Basic** and **form login** (two ways to supply credentials),
- enable **CSRF protection** (matters for `POST`/`PUT`/`DELETE`; topic 11),
- add a set of **security response headers** (`X-Content-Type-Options`, cache-control, etc.),
- create an **HTTP session** to remember who you are between requests.

You wrote none of that. It's the auto-configured baseline.

### The generated password

With no user configured, Spring Boot's `UserDetailsServiceAutoConfiguration` creates one in-memory user named `user` with a random password, logged **once** at startup:

```
Using generated security password: 3f2a9c7e-1b8d-4a2e-9f0c-6d5b4a3c2e1f

This generated password is for development use only. Your security configuration
must be updated before running your application in production.
```

Two things to internalize: the password **changes on every restart** (so it's useless for anything but poking at the app locally), and Spring Boot itself tells you, in the log, that this is not a real configuration. It exists so a fresh app isn't *both* locked *and* impossible to get into — not as an auth strategy. Configuring real users is topic 04.

### Where the lock actually happens — the filter chain

Adding the starter registers one servlet filter, `springSecurityFilterChain` (a `FilterChainProxy`), ahead of Spring MVC's `DispatcherServlet`. Every HTTP request passes through it *before* routing to a controller:

```
request ─▶ [ springSecurityFilterChain ]
             ├─ figure out if the request is already authenticated (session? Basic header?)
             ├─ if not, and the rule says authenticated() ──▶ reject: 401 / redirect to login
             └─ if allowed ──▶ DispatcherServlet ──▶ BookController
```

This is why the anonymous `curl` in the demo never reaches `BookController.all()` — the filter chain rejects it first. Security decisions happen *before* your code runs, which is exactly where you want them: your controller can assume, by the time it executes, that the caller was allowed in.

### The three responses in the demo

`DefaultSecurityTest` (deterministic, using a pinned `demo`/`demo` user) asserts what a real client sees:

| Request | Result | Why |
|---------|--------|-----|
| `GET /api/books`, no credentials | **401** + `WWW-Authenticate: Basic` | `anyRequest().authenticated()` and you're anonymous |
| `GET /api/books`, wrong password | **401** | credentials present but invalid |
| `GET /api/books`, correct Basic creds | **200** + the book list | authenticated, so the request reaches the controller |

Run it against a browser instead of `curl` and the anonymous case gives you a **login page (302 → `/login`)**, not a `401`. Same chain — Spring Security picks the form-login entry point when the client sends `Accept: text/html`, and the Basic `401` otherwise. Topic 06 unpacks that choice.

---

## When to rely on this / when not to

**The deny-by-default posture is exactly right to keep.** Starting from "everything locked" and opening specific routes is the safe direction. Every real configuration in this module builds on it.

**The generated password and default `user` are *only* for first-boot sanity checks.** Never:
- rely on the generated password for anything shared or persistent (it's gone on restart),
- hardcode a `spring.security.user.password` into `application.yml` and ship it (that's a committed credential — a `COMMON_MISTAKES` entry waiting to happen),
- treat "adding the starter" as "the app is now secure." It's authenticated with one throwaway user and *no authorization rules beyond logged-in-or-not*. Real security is the user model (topic 04) and the authorization rules (topic 07) you add on top.

---

## Common pitfalls

- **Thinking security is "done" once endpoints return 401.** All you've proven is the default chain is active. There are no roles, no real users, and every authenticated caller can hit every endpoint. That's a starting line, not a finish line.
- **Chasing the generated password after a restart.** It changed. If you want a stable local login, set `spring.security.user.name`/`password` *for local runs only*, or better, define a real `UserDetailsService` (topic 04).
- **Being surprised that `POST` breaks but `GET` works.** CSRF protection is on by default and blocks unsafe methods without a token — you'll get a `403` on `POST` even with valid credentials until you understand CSRF (topic 11). It's not a bug; it's the default protecting you.
- **Expecting a `401` in the browser and getting a login page.** The default chain has both entry points and chooses by `Accept` header. `curl` (no `text/html`) → `401`; browser → login form. Test with the client type your real callers use.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/02-spring-security-defaults/defaults-demo`.

1. `mvn -q spring-boot:run`, copy the generated password from the log, and run the three `curl` commands in the demo README. Confirm the `401`, then the `200` with `-u user:<password>`.
2. Restart the app. Note the password in the log is **different**. Your previous `curl` command now fails — that's the "regenerated on every restart" point made concrete.
3. Try `curl -i -X POST http://localhost:8080/api/books -u user:<password> -H 'Content-Type: application/json' -d '{}'`. You'll get a `403`, not a `401` — CSRF, not authentication. Note the distinction; topic 11 explains it.
4. Open `http://localhost:8080/api/books` in a browser. You get a login form instead of a `401`. Same chain, different entry point.

## Code examples (reading order)

1. **`pom.xml`** — the one added dependency (`spring-boot-starter-security`) that causes everything below.
2. **`App.java`** — bootstrap, unchanged from any Spring Boot app.
3. **`Book.java`** — the domain record; note it knows nothing about security.
4. **`BookController.java`** — an ordinary open controller; no security code, yet now locked.
5. **`application.yml`** — deliberately configures *no* user, so the generated password appears.
6. **`DefaultSecurityTest.java`** — asserts anonymous → 401, wrong password → 401, valid Basic → 200.

## Self-check

1. You added `spring-boot-starter-security` and changed no other code. Why does `GET /api/books` now return `401` when it returned `200` before?
2. Where does the `401` come from — your `BookController`, the `DispatcherServlet`, or something before both? Explain what runs first for every request.
3. Why is the auto-generated password unsuitable for anything beyond local testing, and what does Spring Boot itself say about it at startup?
4. An anonymous browser hits the app and sees a login page; an anonymous `curl` sees a `401`. It's the same default chain — what makes them differ?
