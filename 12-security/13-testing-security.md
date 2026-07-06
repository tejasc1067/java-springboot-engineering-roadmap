# Testing security — asserting 401, 403, and 200

A security rule you don't test is a rule you're guessing about. Topic 07 warned that a forgotten `@EnableMethodSecurity` leaves `@PreAuthorize` silently ignored — an endpoint that *looks* protected and isn't. The only way to know your `permitAll`/`authenticated`/`hasRole` config actually does what you think is to assert it: anonymous gets `401`, the wrong role gets `403`, the right one gets `200`. This topic covers `spring-security-test` — `@WithMockUser`, the request post-processors (`httpBasic()`, `user()`, `csrf()`), when to inject a fake user versus send real credentials, and the one trap that bites everyone: MockMvc is not the real server.

---

## The problem this solves

Security bugs are quiet. A wrong `requestMatchers` order, a missing annotation, a `.csrf().disable()` copied into a session app — none of these throw at startup. They surface as either a locked-out legitimate user (annoying) or an open door (a breach). A test that says "`alice` must get `403` on `/api/admin`" turns a silent misconfiguration into a red build. And because security is exactly the code you least want to hand-test by clicking around, it's the code that most repays automated assertions.

---

## How it works

### The two ways to be "logged in" in a test

`spring-security-test` gives you two fundamentally different tools, and picking the wrong one hides bugs:

- **Inject a mock context** — `@WithMockUser` (annotation) or `.with(user("alice").roles("ADMIN"))` (per-request). This *skips authentication entirely* and drops a ready-made `Authentication` into the `SecurityContext`. Fast, and perfect for testing **authorization rules and controller logic** — "given an admin, does this endpoint allow it?"
- **Send real credentials** — `.with(httpBasic("alice","password"))`, a real bearer token (topic 09), `.with(oidcLogin())` (topic 10). This runs the **actual authentication chain**: the `UserDetailsService`, the `PasswordEncoder`, the token decoder. Use it to test that **authentication itself works**.

The distinction is the whole lesson:

```java
// Tests AUTHORIZATION — does an admin reach the admin area? (mock context, no password checked)
@Test @WithMockUser(roles = "ADMIN")
void adminReachesAdminArea() throws Exception {
    mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
}

// Tests AUTHENTICATION — is a bad password actually rejected? (real chain: UserDetailsService + BCrypt)
@Test
void wrongPasswordIsRejected401() throws Exception {
    mvc.perform(get("/api/me").with(httpBasic("alice", "wrong-password")))
       .andExpect(status().isUnauthorized());
}
```

`@WithMockUser` would "log in" happily with any password — so a test that only ever uses mock users can pass green while your `PasswordEncoder` is misconfigured to match everything. At least one real-credential test guards the front door. (`@WithUserDetails("alice")` is a middle ground: it loads the *real* user from your `UserDetailsService` — real authorities — but still skips the password check.)

### The three assertions

Every protected endpoint has three cases worth a test:

```java
@Test void anonymousIsUnauthorized401() throws Exception {                 // no auth
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
}
@Test @WithMockUser(roles = "USER") void wrongRoleIsForbidden403() throws Exception {   // authed, wrong role
    mvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
}
@Test @WithMockUser(roles = "ADMIN") void rightRoleIsOk200() throws Exception {          // authed, right role
    mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
}
```

The `401`-vs-`403` split from topic 01 is the thing to get right: `401` for *no/invalid* credentials, `403` for *authenticated-but-forbidden*. Asserting both, separately, is what proves your handlers (topic 12) and rules behave.

### `roles` vs `authorities` in `@WithMockUser`

A direct callback to topic 07's `ROLE_` prefix rule:

```java
@WithMockUser(roles = "ADMIN")           // -> authority ROLE_ADMIN (the prefix is added for you)
@WithMockUser(authorities = "ROLE_ADMIN") // -> authority ROLE_ADMIN (verbatim, no prefix added)
```

Both satisfy `hasRole('ADMIN')`. The classic mistake is `authorities = "ADMIN"` (no prefix) with a `hasRole('ADMIN')` rule — the authority is `ADMIN`, the rule wants `ROLE_ADMIN`, and the test fails (or worse, you "fix" the rule and open a hole). Use `roles=` *or* `authorities="ROLE_..."`, never `authorities="ADMIN"`.

### CSRF in tests

If CSRF protection is on (session apps, and browser-auto-sent schemes — topic 11), a state-changing request needs a token. `spring-security-test` supplies one with `.with(csrf())`, independent of authentication:

```java
@Test @WithMockUser
void postNeedsCsrf() throws Exception {
    mvc.perform(post("/api/books")).andExpect(status().isForbidden());          // no token -> 403
    mvc.perform(post("/api/books").with(csrf())).andExpect(status().isOk());    // token -> 200
}
```

A missing `.with(csrf())` produces a `403` that looks like an authorization failure but isn't — a common source of confused "why is my authenticated POST forbidden?" test failures.

### MockMvc is not the real server

This is the trap, and it's a real one — this module hit it. `MockMvc` runs the filter chain and your controllers *in-process*; it does **not** run a real servlet container. Two consequences that have burned people:

- **No `/error` dispatch.** When a handler calls `response.sendError(403)`, a real container re-dispatches to `/error` — which can re-enter the security chain as anonymous and turn your `403` into a `401` (topic 08 demonstrated this with `curl`). MockMvc skips that dispatch, so the *same code* shows `403` in the test and `401` in production. Your green test lied.
- **`TestRestTemplate` follows redirects.** An anonymous request to a form-login app returns `302 → /login`; `TestRestTemplate` follows it and hands you `200` (the login page), not the `401`/`302` you meant to assert. (Fix: send an explicit `Accept: application/json`, or assert with MockMvc which doesn't follow redirects.)

The takeaway isn't "don't use MockMvc" — it's fast and correct for the vast majority of assertions. It's: **for the exact status code on a security failure, know MockMvc's blind spots, and confirm the important ones against a real HTTP client.** This demo deliberately uses `setStatus`-based handlers (no `sendError`), so its `403` is genuinely `403` in both MockMvc and `curl` — verify that yourself in step 3 below, then break it and watch them diverge.

---

## When to use which tool

- **`@WithMockUser` / `.with(user(...))`** — the default for authorization and controller tests. Fast, declarative, no password machinery.
- **`.with(httpBasic(...))` / real token / `.with(oidcLogin())`** — when the *authentication mechanism* is under test, or for a few end-to-end "the real front door works" tests. Always have at least one.
- **`@WithUserDetails("alice")`** — when you want the user's *real* authorities from your `UserDetailsService` (not hand-typed roles) but don't need to test the password.
- **A real HTTP client (`RestClient`/`TestRestTemplate` with care, or an integration test)** — when the precise status/headers of a security failure matter and you've been bitten by a MockMvc blind spot.

---

## Common pitfalls

- **Only ever using `@WithMockUser`.** It bypasses authentication, so it can't catch a broken `PasswordEncoder`, a bad `UserDetailsService`, or a token-validation bug. Keep at least one real-credential test.
- **`authorities = "ADMIN"`** with a `hasRole('ADMIN')` rule — missing the `ROLE_` prefix. Use `roles="ADMIN"` or `authorities="ROLE_ADMIN"`.
- **Forgetting `.with(csrf())`** on a state-changing request when CSRF is on → a `403` you misread as an authorization bug.
- **Trusting a MockMvc status code for a `sendError`-based handler.** It may differ from the real server (the `/error` dispatch). Prefer `setStatus` handlers (topic 12) and spot-check with `curl`.
- **Only testing the happy path.** The `403` and `401` cases are the ones that protect you; a suite that only asserts `200` proves nothing about security. Test the *denials* explicitly.
- **`@WithMockUser` on a method-security test without `@EnableMethodSecurity`.** The `@PreAuthorize` is ignored, the call succeeds for everyone, and a naive test that only checks the allowed case stays green over a wide-open method. Test the denied case too — that's what fails loudly (topic 07).

---

## Try this yourself

The demo project is `CODE_EXAMPLES/13-testing-security/testing-security-demo` — a Basic-auth app (CSRF on, method security on) with a 13-case tour in `SecurityTestingTest`.

1. **Run the tests** (`mvn test`) — read them top to bottom; they're grouped: the three outcomes, `@WithMockUser` variants, post-processors, mock-vs-real, CSRF, and method security.
2. **Prove real credentials catch what mocks can't.** Temporarily change the `wrongPasswordIsRejected401` test to expect `200` — it fails, because the real BCrypt check rejects the bad password. Now imagine that test used `@WithMockUser` instead: it would pass, hiding the problem. Revert.
3. **Confirm MockMvc and the real server agree here — then make them disagree.** The `403` tests pass. Boot the app and `curl -u alice:password http://localhost:8080/api/admin/stats` → also `403`. Now replace the `accessDeniedHandler` in `SecurityConfig` with the default (delete the `.accessDeniedHandler(...)` line), re-run: the MockMvc `403` test still passes, but `curl` now returns `401` (the `/error` re-dispatch). That divergence is the trap. Restore the handler.
4. **Feel the `roles`/`authorities` prefix bug.** Change `@WithMockUser(authorities = "ROLE_ADMIN")` to `authorities = "ADMIN"` and watch that test flip to `403` — the authority no longer matches `hasRole('ADMIN')`.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`SecurityConfig.java`** — the app under test: URL rules, `@EnableMethodSecurity`, CSRF on, `setStatus` handlers, real BCrypt users.
3. **`BookController.java`** — public/authenticated/admin routes, a CSRF-protected `POST`, and a `@PreAuthorize` `DELETE`.
4. **`SecurityTestingTest.java`** — the 13-case tour: `@WithMockUser` (and `@WithAnonymousUser`), `user()`/`httpBasic()`/`csrf()`, mock-vs-real, and method security.

## Self-check

1. What's the difference between `@WithMockUser` and `.with(httpBasic("alice","password"))` in what they actually exercise? Which one would catch a misconfigured `PasswordEncoder`?
2. For one protected admin endpoint, what three tests would you write, and what status does each assert?
3. `@WithMockUser(authorities = "ADMIN")` on a `hasRole('ADMIN')` endpoint returns `403` in your test. Why, and what are the two correct fixes?
4. Your authenticated `POST` test returns `403` and you're sure the user has the right role. What's the most likely missing piece?
5. Your MockMvc test asserts `403` and passes, but QA reports the endpoint returns `401` in the deployed app. What's a likely cause, and how would you confirm it?
