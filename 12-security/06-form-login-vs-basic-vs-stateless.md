# Form login vs HTTP Basic vs stateless — how the caller stays authenticated

Topics 02–05 got a caller authenticated. This topic is about what happens *between* requests: does the server remember you (a session), or does every request stand alone (stateless)? That single choice drives your login mechanism, how you scale across servers, and whether CSRF is even a concern. Getting it right is the fork in the road before the token topics (08–10), which are all stateless.

---

## The problem this solves

HTTP is stateless: the server doesn't inherently know that this request came from the same caller as the last one. Something has to carry identity forward. There are two strategies, and the whole topic is choosing between them:

1. **Server-side session.** The server authenticates you once, stores "this is alice" in memory (an `HttpSession`), and hands you a `JSESSIONID` cookie. Every later request presents the cookie; the server looks up the session. The credential is sent *once*.
2. **Stateless.** The server stores nothing. Every request must carry a self-contained proof of identity — HTTP Basic credentials, or a token (topics 08–10). The server verifies it fresh each time.

Layered on top of that is *how the credential first arrives*: an HTTP Basic header, an HTML login form, or a token. Those are the three mechanisms in the title.

---

## How it works — the three mechanisms

### HTTP Basic

The client sends `Authorization: Basic base64(username:password)` on **every** request (that's what `curl -u` does). Simple, no login page, no session required.

```
GET /stateless/me
Authorization: Basic YWxpY2U6cGFzc3dvcmQ=      # alice:password, every single request
```

In **Spring Security 6, HTTP Basic is stateless by default** — it re-authenticates each request and does not create a session (this changed from Spring Security 5). That makes it a natural fit for scripts, service-to-service calls, and tests. Its weakness: the credentials travel on every request, so it lives or dies by HTTPS, and it has no clean "log out" (the client just stops sending them).

### Form login

The browser gets an HTML form, the user submits it once (`POST /login` with `username`/`password`), and on success the server:

- creates an `HttpSession` and stores the authenticated `SecurityContext` in it,
- returns a `JSESSIONID` cookie,
- redirects to the originally requested page.

Every later request rides the cookie — no credentials resent. This is the classic **server-rendered web app** model. Note the *shape* of an unauthenticated response differs from Basic: form login **redirects to the login page** (a `302`), because it's built for browsers, whereas Basic returns a `401` challenge. You saw exactly this split back in topic 02.

### Stateless (tokens)

`SessionCreationPolicy.STATELESS` tells Spring Security to never create or use a session. Identity must come entirely from the request itself — typically a bearer token the client obtained earlier:

```
GET /api/thing
Authorization: Bearer eyJhbGciOiJIUzI1Nid…      # self-contained, verified fresh each request
```

No `JSESSIONID`, nothing stored server-side. This is what topics 08 (self-signed JWT), 09 (resource server), and 10 (OAuth2 login) all build on. HTTP Basic is *also* effectively stateless, but a bearer token is preferred for real APIs because it can carry claims, expire, and be revoked without shipping the password on every call.

### What the demo shows

Two chains, one per strategy:

| Endpoint | Chain | Anonymous request | After auth | Session? |
|----------|-------|-------------------|-----------|----------|
| `/stateful/me` | form login | `302` → `/login` | rides `JSESSIONID` cookie | **yes** — reusable |
| `/stateless/me` | HTTP Basic + `STATELESS` | `401` challenge | credentials on every request | **no** — none created |

`SessionVsStatelessTest` proves the stateful chain creates a session that authenticates a follow-up request *with no credentials resent*, while the stateless chain leaves no session behind and demands credentials every time.

---

## When to use which

| Mechanism | State | Use it for | Avoid it for |
|-----------|-------|-----------|--------------|
| **HTTP Basic** | stateless | internal tools, service-to-service, scripts, tests | public browser apps (no logout, creds every request) |
| **Form login** | session | server-rendered web apps with server-side views | SPAs, mobile apps, service-to-service |
| **Token (Bearer/JWT)** | stateless | SPAs, mobile, microservices, public APIs | simple internal server-rendered pages (session is simpler) |

The decision that matters most for backend APIs is **session vs stateless**, and it turns on scaling and clients:

- **Sessions don't scale for free.** "Remember alice" lives in *one* server's memory. Behind a load balancer, request two may hit a different server that never heard of her. You then need **sticky sessions** (pin a user to one server) or a **shared session store** (Redis, topic 18) — extra infrastructure. Stateless auth sidesteps this entirely: any server can verify a token, so you scale horizontally by just adding servers.
- **Non-browser clients don't want cookies.** Mobile apps and other services deal in tokens far more naturally than cookie jars.

---

## Common pitfalls

- **Assuming HTTP Basic creates a session (or that it did in older Spring).** In Spring Security 6 it doesn't — it's stateless by default. If you *rely* on a session after Basic auth (e.g., to store something), you'll be surprised it's gone next request. Use form login or an explicit session policy if you actually want a session.
- **Going stateless but keeping CSRF enabled the wrong way — or disabling it blindly.** CSRF attacks ride the browser's *automatic cookie* sending. A stateless token API (token in an `Authorization` header, not a cookie) isn't exposed to CSRF, so disabling CSRF there is correct. A session/cookie app absolutely needs CSRF on. Topic 11 covers this precisely — don't cargo-cult `.csrf().disable()`.
- **Sessions behind a load balancer with no sticky sessions or shared store.** Works in dev on one node, then randomly "logs users out" in production as requests bounce between servers. Decide sessions-vs-stateless *before* you scale out.
- **Treating "log out" as universal.** Sessions log out by invalidating the session server-side. HTTP Basic has no real logout. Self-contained tokens can't be "un-issued" without extra machinery (a denylist / short expiry) — a tradeoff topic 08 revisits.
- **Long-lived sessions as a memory leak.** Every logged-in user holds server memory until the session times out. Huge user counts × long timeouts = real heap pressure (recall module 03d). Stateless auth has no such cost.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/06-form-login-vs-basic-vs-stateless/session-demo`.

1. Run it. `curl -i -u alice:password http://localhost:8080/stateless/me` and confirm there is **no** `Set-Cookie` header. Then open `/stateful/me` in a browser, log in, and watch a `JSESSIONID` cookie appear (dev tools → Application → Cookies).
2. On the stateful side, log in, then delete the `JSESSIONID` cookie and refresh. You're bounced to the login page — the session *was* your identity.
3. Hit `/stateless/me` anonymously (→ `401`) and `/stateful/me` anonymously (→ `302` redirect to `/login`). Same "who are you?" question, two different answers, because one chain is built for APIs and the other for browsers.
4. In `SecurityConfig`, remove `.sessionCreationPolicy(STATELESS)` from the stateless chain and re-run the test `statelessCreatesNoSession`. With Basic it likely still passes (Basic is stateless by default in SS6) — evidence for the pitfall above.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`WhoAmIController.java`** — one handler behind both `/stateful/me` and `/stateless/me`, echoing the authenticated user.
3. **`SecurityConfig.java`** — the two chains: form login (session) and HTTP Basic (`STATELESS`), plus an in-memory `alice`. The heart of the topic.
4. **`SessionVsStatelessTest.java`** — form login creates a reusable session (and redirects when anonymous); Basic authenticates per request (401 when anonymous) and creates no session.

## Self-check

1. After a successful login, how does the *server-side session* strategy carry your identity to the next request, and how does the *stateless* strategy do it instead?
2. An anonymous request to a form-login endpoint and to an HTTP-Basic endpoint get two different responses. What are they, and why does each mechanism answer differently?
3. Why do server-side sessions complicate horizontal scaling, and what two options fix it? Why does stateless auth avoid the problem?
4. In Spring Security 6, does HTTP Basic create a session by default? What does that imply if you were counting on one?
5. Why is it safe to disable CSRF on a stateless bearer-token API but dangerous to disable it on a cookie/session web app? (One sentence — topic 11 goes deeper.)
