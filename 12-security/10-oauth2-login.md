# OAuth2 / OIDC login — "Log in with Google", the authorization-code flow

Topic 09 put your app in the **resource server** role: a token arrived and you validated it. This topic is the other side — your app becomes an OAuth2 **client** that *obtains* the identity by logging the user in through an external provider. This is the "Log in with Google" button. The user authenticates at Google; Google tells your app who they are; your app never sees the password. You'll wire it with one line (`oauth2Login()`), trace the **authorization-code flow** end to end, and see why this topic — unlike 08 and 09 — is a stateful, session-based, CSRF-on browser app.

---

## The problem this solves

You want users to sign in, but you don't want to store passwords, run a "forgot password" flow, handle 2FA, or become the thing attackers phish. Delegate all of it: let Google (or your company's SSO, or Keycloak) authenticate the user and vouch for them. Your app receives a signed statement of *who the user is* and starts a session. You handle zero credentials.

There are three parties, and naming them makes the rest clear:

- **Resource owner** — the user.
- **Client** — *your app* (this topic). It wants to know who the user is.
- **Authorization server / IdP** — Google. It authenticates the user and issues tokens.

In topic 09 your app was the resource *server* validating a token; here it's the *client* getting one. Checkpoint: a resource server **validates** a token it received; a client **obtains** one by driving login. One app can be both.

---

## How it works

### The authorization-code flow, step by step

```
 browser (user)                your app (client)              Google (IdP)
      │                              │                            │
   1  │ GET /api/me (no session) ───▶│                            │
   2  │ ◀── 302 to /oauth2/authorization/google                   │
   3  │ ── follow ──▶ 302 to accounts.google.com?response_type=code&client_id&redirect_uri&scope&state&nonce ─▶
   4  │ ────────────── user signs in AT GOOGLE (password never touches your app) ──────────────▶│
   5  │ ◀── 302 back to  {app}/login/oauth2/code/google?code=AUTH_CODE&state ───────────────────│
   6  │ ── GET that callback ──▶│                            │
   7  │                         │ ── POST code + client_secret (back channel) ──▶│   (exchange)
   8  │                         │ ◀── access_token + id_token (a JWT) ───────────│
   9  │                         │  validate id_token (sig via JWK set, iss, aud, exp, nonce); build OidcUser; create session
  10  │ ◀── 302 to /api/me  + Set-Cookie: JSESSIONID ──────────│                            │
  11  │ ── GET /api/me (with cookie) ──▶│  authenticated → 200  │
```

The subtle, important parts:

- **Step 3 sends only a `code` request, not credentials.** The user types their password into *Google's* page (step 4), never yours.
- **Steps 7–8 happen on the back channel** — server-to-server, carrying your `client_secret`. The token never rides in the browser URL. That's the whole reason the *code* flow exists (see the broken version below).
- **`state` (step 3/5)** is a random value your app stores and re-checks on the callback — it stops an attacker from feeding you a code they obtained (login CSRF).
- **`nonce`** is an OIDC addition tying the id_token to *this* login, preventing id_token replay.
- **The id_token** is a JWT the app validates exactly like topic 09 validated its tokens — signature against Google's JWK set, plus `iss`/`aud`/`exp`/`nonce`. OIDC reused the resource-server machinery you already know.

The demo shows steps 1–3 concretely. Hitting a protected route while anonymous:

```
GET /api/me                    -> 302  Location: /oauth2/authorization/google
GET /oauth2/authorization/google -> 302 Location:
    https://accounts.google.com/o/oauth2/v2/auth
      ?response_type=code&client_id=...&scope=openid%20profile%20email
      &state=...&nonce=...&redirect_uri=http://localhost:8080/login/oauth2/code/google
```

### One line configures all of it

```java
.oauth2Login(Customizer.withDefaults())
```

That installs the filters that build the step-3 redirect, handle the step-6 callback, do the step-7 exchange, validate the id_token, and create the session. The provider details come from config — and because `google` is a well-known provider, you supply only credentials and scopes:

```yaml
spring.security.oauth2.client.registration.google:
  client-id:     ${GOOGLE_CLIENT_ID:...}
  client-secret: ${GOOGLE_CLIENT_SECRET:...}   # a real secret — load from env, never commit
  scope: openid, profile, email                # "openid" is what makes this OIDC (you get an id_token)
```

### OAuth2 vs OIDC, and why the principal is an `OidcUser`

Plain **OAuth2** is about *authorization* — getting permission to access resources. **OIDC** (OpenID Connect) is a thin layer on top that adds *authentication* — the `id_token` states who the user is. "Log in with Google" is OIDC: you request the `openid` scope and get back an id_token, so Spring makes the principal an `OidcUser` and you can read standard claims:

```java
@GetMapping("/api/me")
public Map<String,Object> me(@AuthenticationPrincipal OidcUser user) {
    return Map.of("subject", user.getSubject(), "email", user.getEmail(), "name", user.getFullName());
}
```

Drop the `openid` scope and you get a plain OAuth2 login instead — no id_token, and the principal is an `OAuth2User`, not an `OidcUser`.

### It's stateful — on purpose

Topics 08–09 were stateless (bearer token on every request, CSRF off). This is the opposite: the flow stores `state`/`nonce` in the session *between* the redirect and the callback, and after login the session cookie is what keeps you signed in. So there is no `SessionCreationPolicy.STATELESS` and **CSRF stays on** — it's a cookie-based browser app, exactly the case topic 06's session side and topic 11's CSRF discussion are about.

### What the demo proves

| Request | State | Result |
|---------|-------|--------|
| `GET /` | anonymous | 200 (public) |
| `GET /api/me` | anonymous | **302 → `/oauth2/authorization/google`** (into the flow, not a 401) |
| `GET /oauth2/authorization/google` | anonymous | **302 → Google** with `response_type=code`, `client_id`, `scope=openid…`, `state`, `nonce`, `redirect_uri` |
| `GET /api/me` | logged in (OIDC) | 200 → `subject`, `email`, `name` from the id_token |

---

## Vulnerable / broken version

The flow's design *is* the security lesson, so the "broken" version is an older flow it replaced: the **implicit flow**. There, instead of returning a short-lived `code` to be exchanged on the back channel, the provider returned the **access token directly in the browser URL fragment** (`#access_token=...`). That token then sat in browser history, `Referer` headers, and any script on the page — trivially stealable. The **authorization-code flow** fixes this by returning only a one-time code and exchanging it server-to-server with the `client_secret`, so the token never touches the browser. Implicit is now deprecated; code flow (with PKCE for clients that can't hold a secret) is the answer.

Two more ways people break the code flow by hand — all of which Spring handles for you, so the danger is *rolling your own*:

- **Skipping the `state` check** → login CSRF: an attacker tricks the victim's browser into completing a login with the *attacker's* code, silently logging the victim into the attacker's account.
- **Committing the `client_secret`** → anyone can impersonate your app to the IdP. It's a secret; treat it like a password.

---

## When to use it / when not

- **Use `oauth2Login`** when real users sign in through a browser and you want to delegate authentication — "Log in with Google/GitHub/your-corporate-SSO." You store no passwords.
- **Don't confuse it with a resource server.** If your service just receives tokens from callers (mobile app, another service) and validates them, that's topic 09 — you're not running a login. A single app *can* be both: log users in *and* hold their access token to call downstream APIs on their behalf (a common "backend-for-frontend" shape).
- **Not for machine-to-machine.** No user, no browser, no consent screen → the authorization-code *login* flow doesn't apply; that's the client-credentials grant instead.
- **Stateful by nature.** If you truly need a stateless API, you don't want interactive login on it — you want bearer tokens (topics 08/09). Pick the model per surface.

---

## Common pitfalls

- **Redirect-URI mismatch** — the number-one real-world error. The URI you register at the provider must match `{baseUrl}/login/oauth2/code/{registrationId}` *exactly* — scheme, host, port, path. `http` vs `https`, a trailing slash, or the wrong port → the provider refuses with `redirect_uri_mismatch` before your app is even involved.
- **Forgetting the `openid` scope** — without it you get a plain OAuth2 login, no id_token, and `@AuthenticationPrincipal OidcUser` is null/wrong (the principal is an `OAuth2User`). Request `openid` for "log in."
- **Making it stateless** — setting `SessionCreationPolicy.STATELESS` breaks the flow: `state`/`nonce` live in the session between the redirect and the callback. Login needs a session.
- **Committing the client secret** — it's a credential. Load it from the environment; keep it out of git.
- **Behind a proxy/load balancer** — the `redirect_uri` is built from the incoming request. If forwarded headers (`X-Forwarded-Proto`/`-Host`) aren't honored, the app builds an internal `http://…` URI that won't match what you registered. Configure forwarded-headers handling.
- **Expecting an `OidcUser` from a non-OIDC provider** — GitHub, for instance, is plain OAuth2, so its principal is an `OAuth2User` and `getEmail()`/id-token claims differ. Match your code to whether the provider is OIDC.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/10-oauth2-login/oauth2-login-demo`. It boots offline with placeholder credentials so you can watch the flow *start*; completing it needs a real provider (README has Google and Keycloak setups).

1. **Run the tests** (`mvn test`) — home is public, an anonymous protected route redirects into the flow, the authorization endpoint builds the correct Google redirect, and a simulated OIDC login exposes the identity.
2. **Watch the redirect chain.** Boot (`mvn spring-boot:run`), then `curl -i http://localhost:8080/api/me` → 302 to `/oauth2/authorization/google`; `curl -i` *that* → 302 to Google. Read every query parameter: `response_type=code`, `state`, `nonce`, `redirect_uri`. You're looking at step 3 of the diagram.
3. **Complete it for real.** Create a Google OAuth client (or run Keycloak), put the client id/secret in env vars, restart, and open `http://localhost:8080/api/me` in a browser. Sign in; `/api/me` returns your email. (README has exact steps.)
4. **Break the redirect URI.** At the provider, change the registered redirect URI's port, retry the browser login, and read the provider's `redirect_uri_mismatch` error — the most common setup failure, made deliberate.
5. **Drop `openid` from `scope`** and log in again: the principal is no longer an `OidcUser`. That one scope is the line between OAuth2 and OIDC.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`application.yml`** — the `google` client registration: id, secret (from env), and the `openid, profile, email` scopes that make it an OIDC login.
3. **`SecurityConfig.java`** — `oauth2Login(...)`, and the deliberate *absence* of `STATELESS`/`csrf.disable()`. Compare to topics 08–09.
4. **`DemoController.java`** — a public home and a protected `/api/me` reading claims off the `@AuthenticationPrincipal OidcUser`.
5. **`OAuth2LoginTest.java`** — the offline-provable slice: the redirect into the flow, the exact Google authorization URL, and post-login identity via `oidcLogin()`.

## Self-check

1. Name the three parties in the flow and which role *your app* plays here — and which role it played in topic 09.
2. Walk from clicking "Log in with Google" to seeing your name on `/api/me`. Where does the user's password go, and on which channel does the code-for-token exchange happen?
3. What attack does `state` prevent, and what does `nonce` add for OIDC?
4. What's the difference between OAuth2 and OIDC, which one is "Log in with Google," and which token carries the user's identity?
5. Why is this app stateful (session, CSRF on) when topics 08–09 were stateless (bearer token, CSRF off)? What breaks if you force it stateless?
