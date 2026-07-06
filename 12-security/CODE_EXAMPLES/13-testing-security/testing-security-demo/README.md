# 13 — Testing security

A small Basic-auth app (CSRF on, method security on) whose only real purpose is to be *tested*. The 13-case `SecurityTestingTest` is a tour of `spring-security-test`. Users: `alice`/`password` (USER), `admin`/`password` (ADMIN).

## Run the tests

```bash
mvn -q test
```

`SecurityTestingTest` is grouped by technique:

| Group | What it shows |
|-------|---------------|
| The three outcomes | anonymous → `401`, wrong role → `403`, right role → `200` |
| `@WithMockUser` variants | default user, `roles=`, `authorities=` (the `ROLE_` prefix rule), `@WithAnonymousUser` |
| Request post-processors | `.with(user(...))` (mock), `.with(httpBasic(...))` (real credentials) |
| Mock vs real | a bad password is rejected `401` — only a real-credential test catches this |
| CSRF | `POST` without `.with(csrf())` → `403`; with it → `200` |
| Method security | `@WithMockUser` role drives a `@PreAuthorize("hasRole('ADMIN')")` delete |

## The one distinction to take away

- **`@WithMockUser` / `.with(user(...))`** inject a fake `SecurityContext` — they *skip* authentication. Great for testing authorization rules; useless for catching a broken password check.
- **`.with(httpBasic("alice","password"))`** sends real credentials through the `UserDetailsService` + BCrypt chain — the actual login path. Keep at least one of these.

```java
@WithMockUser(roles = "ADMIN")                                   // tests AUTHORIZATION
void adminReachesAdminArea() { get("/api/admin/stats") -> 200 }

.with(httpBasic("alice", "wrong-password"))                      // tests AUTHENTICATION
void wrongPasswordIsRejected() { get("/api/me") -> 401 }
```

## MockMvc vs the real server (verify it)

This app uses `setStatus`-based handlers, so its `403` is a real `403` everywhere:

```bash
mvn -q spring-boot:run
curl -s -o /dev/null -w '%{http_code}\n' -u alice:password http://localhost:8080/api/admin/stats   # 403 (matches the test)
```

Now delete the `.accessDeniedHandler(...)` line in `SecurityConfig` (falling back to the default `sendError` handler) and re-run: the MockMvc `403` test **still passes**, but the `curl` above returns **401** — the servlet `/error` re-dispatch (topic 08). That's the canonical case where MockMvc and the real server disagree. Restore the handler.

## Notes

- CSRF is left on because HTTP Basic is browser-auto-sent (topic 11's table) — which also lets the tests demonstrate `.with(csrf())`.
- `@WithUserDetails("alice")` (not shown) is a middle ground: it loads the real user from the `UserDetailsService` (real authorities) but still skips the password check.
- These same helpers appeared throughout module 12 — `httpBasic()` (topics 07, 12), real bearer tokens (08–09), `oidcLogin()` (10), `csrf()` (11). This topic names and organizes them.
