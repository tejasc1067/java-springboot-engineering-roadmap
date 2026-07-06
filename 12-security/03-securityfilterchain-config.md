# Configuring the SecurityFilterChain — taking control of the defaults

The default chain from topic 02 locks *every* endpoint with `anyRequest().authenticated()`. No real app wants that — a health check, a login page, a public catalog all need to be reachable without credentials. You take control by defining one `@Bean` of type `SecurityFilterChain`. The moment that bean exists, Spring Boot drops its auto-configured chain and uses yours, and you decide — route by route — what's open, what's protected, and how callers authenticate.

---

## The problem this solves

Topic 02's default is all-or-nothing: authenticate for everything, or (if you'd disabled security) authenticate for nothing. Reality is mixed:

- `GET /public/health` — a monitoring probe must reach this with no credentials.
- `GET /api/books` — only logged-in users.
- (later) `DELETE /api/books/{id}` — only admins.

You need per-route rules. And you need to *keep* the safe posture from topic 02: routes you forget to mention should stay locked, not fall open. The `SecurityFilterChain` bean is where you express both.

---

## How it works

### One bean replaces the default

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

`HttpSecurity` is a builder for *the* filter chain. `authorizeHttpRequests` declares the authorization rules; `httpBasic(...)` enables a way to supply credentials; `http.build()` produces the `SecurityFilterChain`. Spring Boot's `SecurityAutoConfiguration` is written to back off when it finds a user-defined `SecurityFilterChain` bean — so this one bean *is* the whole security policy now.

`@EnableWebSecurity` isn't strictly required in a Spring Boot app (Boot imports it for you), but writing it makes the class self-documenting: "this is the web security configuration."

### The rules are ordered — first match wins

`authorizeHttpRequests` evaluates matchers **top to bottom and stops at the first match.** So specificity has to go first:

```java
.requestMatchers("/public/**").permitAll()   // checked first
.anyRequest().authenticated()                 // catch-all, checked only if nothing above matched
```

`GET /public/health` matches line 1 → permitted, and line 2 is never consulted. `GET /api/books` matches nothing specific, falls to `anyRequest()` → must be authenticated. Two rules about order that bite people:

- **`anyRequest()` must be last.** It's the catch-all; Spring throws a startup exception if you declare any `requestMatchers(...)` after it. Structurally it forces you to handle "everything else" explicitly.
- **A broad rule before a narrow one makes the narrow one dead code.** If you wrote `.requestMatchers("/api/**").permitAll()` *before* `.requestMatchers("/api/admin/**").authenticated()`, every `/api/admin/...` request matches the broad `/api/**` line first and is permitted — the admin rule never runs. Order specific-before-general.

### `permitAll()` still runs the filter chain

A subtle but important point: `permitAll()` does **not** mean "skip security for this route." The security filters still execute; `permitAll()` only means the *authorization* decision is "allow, even anonymous." The request still gets a `SecurityContext`, security headers, CSRF handling, and so on. (There is a way to truly bypass the filters — `web.ignoring()` — but it's discouraged precisely because it turns *off* those protections; prefer `permitAll()`.)

### Enabling a way to authenticate

`authorizeHttpRequests` says *who may access what*; it doesn't say *how a caller proves who they are*. That's the job of a mechanism like `httpBasic(...)` or `formLogin(...)`. This demo enables **HTTP Basic only**:

```java
.httpBasic(Customizer.withDefaults());
```

Two consequences worth noting. First, a caller authenticates by sending an `Authorization: Basic <base64 user:pass>` header (what `curl -u` does). Second — and this is the deliberate change from topic 02 — with no form login configured, an unauthenticated request always gets a clean **`401`** with a `WWW-Authenticate: Basic` header, instead of the topic-02 redirect-to-login-page for browsers. One entry point, one predictable response. (Choosing between Basic, form login, and tokens is topic 06.)

If you enable *no* mechanism at all, there's no way to log in and unauthenticated requests fall to a default `403` — a common "why can't anyone get in?" confusion.

### What the demo proves

`FilterChainTest` runs the real chain under MockMvc (via `spring-security-test`) and asserts the three outcomes:

| Request | Result | Rule that decided it |
|---------|--------|----------------------|
| `GET /public/health`, anonymous | **200** | `requestMatchers("/public/**").permitAll()` |
| `GET /api/books`, anonymous | **401** | `anyRequest().authenticated()` + HTTP Basic entry point |
| `GET /api/books`, `.with(user("alice"))` | **200** | authenticated, so `authenticated()` is satisfied |

`.with(user("alice"))` is `spring-security-test` injecting an already-authenticated user, so the test exercises *authorization* without needing a real password — exactly the tool you want here.

---

## When to use it / when not to

- **One `SecurityFilterChain` is enough for most apps.** A single bean with a well-ordered set of `requestMatchers` covers the typical case.
- **Multiple chains** (several `SecurityFilterChain` beans, each with a `securityMatcher(...)` and an `@Order`) are for genuinely different security models on different URL spaces — e.g. token auth for `/api/**` and form login for `/admin/**`. Useful, but don't reach for it until one chain can't express what you need; it's easy to get the ordering wrong.
- **Method security** (`@PreAuthorize`) is a *complement*, not a replacement, for URL rules — it's topic 07. URL rules are coarse (by path); method security is fine (by role/expression on a specific method).

---

## Common pitfalls

- **Ordering rules general-before-specific.** The broad matcher wins and the specific one becomes dead code — often silently opening something you meant to lock. Put specific paths first.
- **Declaring a matcher after `anyRequest()`.** Startup fails with an `IllegalStateException`. `anyRequest()` is always last.
- **Forgetting to enable an authentication mechanism.** With `authorizeHttpRequests` but no `httpBasic`/`formLogin`, there's no way to log in and protected routes give a `403` to everyone. Enable one mechanism.
- **Thinking `permitAll()` disables security for that path.** It doesn't; the filters still run. If you truly need to skip them (rare), that's a different, more dangerous tool (`web.ignoring()`).
- **Matching `/public` when you meant `/public/**`.** `"/public"` matches only that exact path; sub-paths like `/public/health` won't match. Use `/**` to include the subtree.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/03-securityfilterchain-config/filterchain-demo`.

1. Run it and confirm the three `curl` results in the demo README: `/public/health` → 200, `/api/books` → 401, `/api/books` with `-u user:<generated>` → 200.
2. **Break the order on purpose.** Change the rules to `.requestMatchers("/api/**").permitAll()` first, then add `.requestMatchers("/api/books/**").authenticated()` after it. Restart and hit `/api/books` anonymously — it's now `200`, because the broad `permitAll` matched first. The `authenticated()` rule is dead. Put it back.
3. **Remove `.httpBasic(...)`.** Restart and call `/api/books` with `-u user:<password>`. You now get a `403` no matter what — there's no configured way to authenticate. Add it back.
4. Add a new route to `BookController`, say `GET /api/books/count`, and confirm it's protected *without* touching `SecurityConfig` — it falls under `anyRequest().authenticated()`. That's fail-safe by default working for you.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record.
3. **`PublicController.java`** — the open route (`/public/health`).
4. **`BookController.java`** — the protected routes (`/api/books`), with no security code of their own.
5. **`SecurityConfig.java`** — the `SecurityFilterChain` bean: `permitAll` vs `authenticated`, order, HTTP Basic. The heart of the topic.
6. **`FilterChainTest.java`** — asserts 200 public / 401 anonymous / 200 authenticated, using `spring-security-test`.

## Self-check

1. You add a `SecurityFilterChain` bean. What happens to Spring Boot's default chain from topic 02, and why?
2. Given `requestMatchers("/public/**").permitAll()` then `anyRequest().authenticated()`, walk through what happens to `GET /public/health` and to `GET /api/books`. Which rule decides each, and why is order the reason?
3. Why does `permitAll()` *not* mean "the security filters don't run for this path"? What does it actually decide?
4. You configure `authorizeHttpRequests` but forget `httpBasic`/`formLogin`. A user sends correct credentials and still can't get in. What status do they get, and why?
