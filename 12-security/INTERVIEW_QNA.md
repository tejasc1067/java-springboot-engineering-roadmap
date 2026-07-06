# Interview Q&A — Security

The questions you'd actually be asked about a Spring Boot service's security. The answers explain *why* and *when*, not just *what* — these are the ones where reciting a definition gets you nowhere.

---

## 1. Walk a request through Spring Security. Where does the filter chain sit, and where are authentication and authorization decided?

Spring Security is a chain of **servlet filters** that runs *before* the `DispatcherServlet` (and therefore before your controllers). A single `FilterChainProxy` (registered as the servlet filter `springSecurityFilterChain`) delegates to one `SecurityFilterChain` whose ordered filters each do one job. The ones that matter:

- **`SecurityContextHolderFilter`** — loads any existing `SecurityContext` for the request.
- An **authentication filter** for your mechanism — `UsernamePasswordAuthenticationFilter` (form login), `BasicAuthenticationFilter`, `BearerTokenAuthenticationFilter` (resource server), or your own. It turns credentials/a token into an `Authentication` and puts it in the context. **This is where authentication happens.**
- **`AnonymousAuthenticationFilter`** — if nothing authenticated, installs an anonymous token.
- **`ExceptionTranslationFilter`** — catches `AuthenticationException`/`AccessDeniedException` from downstream and routes them to the `AuthenticationEntryPoint` (401) or `AccessDeniedHandler` (403).
- **`AuthorizationFilter`** (last) — evaluates your `authorizeHttpRequests` rules. **This is where URL authorization happens.**

Only if all of that passes does the request reach the `DispatcherServlet` and your controller (where method security `@PreAuthorize` and controller code run). The key consequence: because authn/authz decisions happen in filters, a `@RestControllerAdvice` never sees them — that's why the 401/403 error contract needs its own handlers (topic 12).

---

## 2. Authentication vs authorization — and what concrete attack does each failure enable?

**Authentication** answers "who are you?"; **authorization** answers "what may you do?" They map to two status codes: `401` (not authenticated) and `403` (authenticated but not permitted).

The attacks are different and that's the point. A broken **authentication** boundary is *impersonation* — an unauthenticated user is treated as someone, or as anyone: an open endpoint that should require login, a forged token accepted, a password check that matches everything. A broken **authorization** boundary is *privilege escalation* — a genuinely-logged-in `USER` doing `ADMIN` things, or reading another user's data: a missing `@PreAuthorize`, a `hasRole` typo, a per-object ownership check that isn't there. You need both: authenticating everyone perfectly still lets any logged-in user delete any record if authorization is missing.

---

## 3. Explain what happens when a user submits a password. Why is the stored value a BCrypt hash, and what do salt and work factor buy you?

On login, the authentication filter builds an unauthenticated token and hands it to the `AuthenticationManager` → a `DaoAuthenticationProvider`, which calls your **`UserDetailsService`** to load the user (including the stored password **hash**) and then asks the **`PasswordEncoder`** whether the submitted raw password `matches` that hash. Your code never compares strings; the encoder does, and the raw password is never stored.

The stored value is a BCrypt hash, not the password, because databases leak — and a hash means a leak doesn't hand over the actual passwords (which users reuse everywhere). BCrypt specifically because:

- **Salt** — a per-password random value mixed into the hash (and stored alongside it). Two users with the same password get different hashes, which defeats precomputed **rainbow tables** and stops an attacker from cracking many accounts at once.
- **Work factor** — a tunable cost (e.g. 2^10 rounds) that makes each hash deliberately slow. A fast hash (MD5/SHA-256) lets a GPU try billions of guesses per second; BCrypt's cost caps that to a trickle, and you raise the factor as hardware improves. The whole design goal is to be *slow*, the opposite of a general-purpose hash.

---

## 4. Server-side sessions vs stateless tokens — what does a `JSESSIONID` buy you, what does it cost, and when do you pick each?

A **session** stores auth state on the server; the client holds only a `JSESSIONID` cookie that points at it. Upsides: you can **revoke instantly** (delete the session), the cookie carries no sensitive data, and it's simple for a server-rendered app. Costs: the state has to live somewhere every instance can reach — so horizontal scaling needs sticky sessions or a shared session store (another moving part) — and because the cookie is sent automatically, you must defend **CSRF**.

A **stateless token** (JWT) carries the auth state *in the token*, signed. Upsides: any instance validates it with no shared state, so it scales horizontally for free, and it's the natural fit for SPAs, mobile, and service-to-service calls. Costs: **you can't un-issue it** — a signed token is valid until it expires, so a stolen token works until `exp`. You manage that with short lifetimes plus refresh, and a denylist only when you truly need instant kill (which quietly reintroduces server state). If the token rides in the `Authorization` header (not a cookie), there's no CSRF vector, so those APIs disable CSRF.

Rule of thumb: sessions for a classic browser app that wants instant logout; tokens for APIs and anything that scales out.

---

## 5. What does a JWT's signature guarantee — and what does it *not*?

A JWT is `header.payload.signature`, three base64url segments. The signature guarantees **integrity** (the header and payload weren't altered — any change invalidates it) and **authenticity** (it was produced by whoever holds the signing key). It does **not** provide **confidentiality**: the payload is base64, not encryption, and is readable by anyone holding the token.

Two practical consequences interviewers look for: (1) never put secrets or PII in the payload — it's public to anyone who sees the token; (2) the security rests entirely on *verifying* the signature and *pinning the algorithm* — a verifier that skips the check, or accepts `alg:none`, accepts forgeries. The signature is what stops a user from editing `"roles":"ROLE_USER"` into `"ROLE_ADMIN"` and being believed.

---

## 6. Resource server vs OAuth2 client/login — which obtains a token and which validates one? Draw the authorization-code flow.

A **resource server** *validates* a token it received on an incoming request (topic 09). An OAuth2 **client** doing login *obtains* identity by driving the user through the provider (topic 10). A single app can be both — log a user in *and* hold their access token to call downstream APIs.

The authorization-code flow (OIDC "Log in with Google"):

1. User hits a protected page; the app redirects the browser to the provider's **authorization endpoint** with `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`, and (OIDC) `nonce`.
2. The user authenticates **at the provider** — their password never touches your app.
3. The provider redirects back to your `redirect_uri` with a one-time **authorization code** (and the `state`).
4. Your app verifies `state`, then **POSTs the code + client secret to the provider's token endpoint on the back channel** (server-to-server).
5. The provider returns an **access token** and (OIDC) an **id_token**; the app validates the id_token like any JWT and establishes a session.

The code (not the token) travels through the browser, and the token exchange happens server-to-server with the client secret — which is exactly why the code flow replaced the old implicit flow (below).

---

## 7. Why does the authorization-code flow exist instead of just returning the token? What do `state` and `nonce` protect?

The deprecated **implicit flow** returned the access token *directly in the browser URL fragment*, where it landed in history, `Referer` headers, and any script on the page — trivially stolen. The **authorization-code flow** returns only a short-lived, single-use code and exchanges it for the token on the back channel with the client secret, so the token never touches the browser. (For clients that can't hold a secret — SPAs, mobile — **PKCE** adds a proof so an intercepted code is useless.)

- **`state`** is a random value the app stores and re-checks on the callback. It prevents **login CSRF**: an attacker can't feed your app a code obtained in *their* session to silently log the victim into the attacker's account.
- **`nonce`** (OIDC) ties the returned id_token to *this* login request, preventing replay of a previously captured id_token.

---

## 8. When is `.csrf().disable()` correct, and when is it a serious bug?

It depends entirely on whether the browser attaches the credential **automatically**. CSRF is possible only when a forged cross-site request rides an ambient credential the browser sends on its own.

- **Cookie/session auth, HTTP Basic, or a JWT stored in a cookie** → auto-sent → **CSRF protection required; disabling it is a bug.** A page on `evil.com` can auto-submit to your app and the browser attaches the cookie.
- **A bearer token in the `Authorization` header (set by JavaScript)** → *not* auto-sent, and a cross-site page can neither set that header nor read your token → **no CSRF vector, so disabling CSRF is correct** (topics 08–09).

The trap: "we use JWT, so we don't need CSRF" is wrong if the JWT is in a **cookie** — it's the transport that creates the risk, not the token format. So the honest answer is "show me how the request proves identity," then decide.

---

## 9. What is CORS actually, who enforces it, and why is it not security for your API?

CORS is a **browser** mechanism built on the Same-Origin Policy: JavaScript on origin A may not *read* a response from origin B unless B opts in with `Access-Control-Allow-*` headers. For non-simple requests the browser sends a preflight `OPTIONS` first and only proceeds if allowed. Crucially, **the browser enforces it, not your server** — your server just emits headers and the browser decides whether to hand the response to the calling script.

So CORS is *not* access control for your API. A non-browser client — `curl`, Postman, another server — ignores CORS entirely and calls your endpoint regardless. CORS only governs which *other web origins* a browser will let read your responses; it protects your users, not your data. If you're reaching for CORS to "secure" an endpoint, you actually want authentication/authorization. And it does nothing against CSRF, because a CSRF attack never needs to *read* the response. The dangerous misconfig is `allowedOrigins("*")` with `allowCredentials(true)` (or reflecting the `Origin`), which invites any site to make credentialed reads on the user's behalf.

---

## 10. Roles, authorities, and scopes — how do they relate, and which check goes with which?

An **authority** is the base unit: a plain string a principal carries, like `ROLE_ADMIN` or `books:write`. A **role** is just an authority conventionally prefixed `ROLE_`; `hasRole("ADMIN")` is sugar that prepends the prefix and checks `ROLE_ADMIN`, while `hasAuthority("ROLE_ADMIN")` checks the exact string. So `roles("ADMIN")` ⇄ `hasRole("ADMIN")` ⇄ authority `ROLE_ADMIN` — and `hasRole("ROLE_ADMIN")` is the classic double-prefix bug (it looks for `ROLE_ROLE_ADMIN`).

A **scope** is the OAuth2 flavor: the token's `scope` claim lists what *the token* is permitted to do, and Spring maps each scope to a `SCOPE_<name>` authority, checked with `hasAuthority("SCOPE_books:read")`. The conceptual difference is subtle but real: a *role* describes what the *user* is (assigned in your system); a *scope* describes what *this token* was granted (decided by the IdP at issue time), which may be a subset of what the user could do. Use `hasRole`/`hasAuthority` for role/authority checks, and scope-based rules for tokens from an OAuth2 provider.

---

## 11. Why can't a `@RestControllerAdvice` handle a 401 or 403, and how do you make security errors match your error contract?

Because security failures are raised in the **filter chain**, before the `DispatcherServlet` is ever invoked — and `@ExceptionHandler` methods run *inside* the servlet, at the end of the pipeline. `ExceptionTranslationFilter` catches the `AuthenticationException`/`AccessDeniedException` and routes it to the `AuthenticationEntryPoint` or `AccessDeniedHandler`; it never propagates to where your advice could see it. So even with a perfect `ProblemDetail` advice, your `401`/`403` come back as a bare status, an empty body, or the whitelabel error page — a different shape from your `400`s and `404`s.

The fix is to customize those two hooks to write the *same* `ProblemDetail`: a custom `AuthenticationEntryPoint` for `401` and `AccessDeniedHandler` for `403`, wired via `.exceptionHandling(...)`. Two gotchas: use the **Spring-configured `ObjectMapper`** (it has the ProblemDetail mixin and JavaTime module — a hand-built one serializes the wrong shape), and write via `setStatus` rather than `sendError` (which triggers an `/error` re-dispatch that can flip your `403` into a `401`).

---

## 12. How do you test security, and what's the difference between `@WithMockUser` and sending real credentials? What can MockMvc miss?

The three cases worth asserting on any protected endpoint: anonymous → `401`, wrong role → `403`, right role → `200`. `spring-security-test` gives two ways to "be logged in," and they test different things:

- **`@WithMockUser` / `.with(user(...))`** inject a ready-made `Authentication` and **skip authentication entirely**. Fast, ideal for testing *authorization* rules and controller logic. But because they never run the auth mechanism, they **cannot catch a broken `PasswordEncoder` or token validation** — a suite of only mock users can be green while login is broken.
- **`.with(httpBasic(...))`, a real bearer token, `.with(oidcLogin())`** run the actual authentication chain (`UserDetailsService`, `PasswordEncoder`, decoder). Keep at least one to guard the front door. (`@WithUserDetails` is the middle ground: real authorities from your `UserDetailsService`, but no password check.)

What MockMvc misses: it runs in-process, not in a real servlet container, so it **doesn't perform the `/error` dispatch** — a handler that uses `sendError` can show `403` in a MockMvc test but return `401` in production. For the exact status of a security failure, know that blind spot and spot-check against a real HTTP client.

---

## 13. Symmetric (HS256) vs asymmetric (RS256) JWT signing — what's the difference, and why do multi-service systems use asymmetric?

**HS256** uses one **symmetric secret** to both sign and verify. It's simple and fine when a *single* app issues and validates its own tokens (topic 08). But anyone who can verify can also sign — so the verifier holds forging power.

**RS256** uses a **key pair**: the issuer signs with a **private** key and publishes the **public** key (as a JWK Set); verifiers use only the public key. A verifier can check signatures but can never mint a token. In a multi-service system with a central IdP, this is essential: you don't want to copy one signing secret into a dozen services (secret sprawl, and any leak lets that service forge tokens for the whole estate). With asymmetric keys the private signing key lives only at the IdP, each resource server holds a harmless public key, and key **rotation** is the IdP's job — verifiers pick up new keys from the JWK Set by `kid` automatically. That's the meaning of topic 09's "no shared secret."

---

## 14. Beyond the signature, what makes a token "valid *for me*" on a resource server?

A valid signature only proves the token is **genuine** (from a trusted issuer, unaltered). "Valid for me" requires more claim checks:

- **`exp`** (and `nbf`) — not expired / already active. A stale token must be rejected.
- **`iss`** — issued by the issuer you trust (configuring `issuer-uri` validates this automatically; a bare public key does not).
- **`aud`** — the token was intended for *this* service. Without it, a token a caller legitimately got for another service (same IdP) is replayable against yours.

Skipping `aud` is the common, subtle hole: everything verifies, the token is real, and it's still not authorized for your service. Validate signature + `iss` + `aud` + `exp` together — and remember authorization (does this token's `scope`/roles permit *this action*?) is a further, separate step on top of "is this token valid."
