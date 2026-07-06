# JWT stateless auth — issue and validate a token you sign yourself

Topic 06 gave you two ends of an axis: server-side sessions (a `JSESSIONID` cookie backed by state the server remembers) and stateless (every request carries its own proof). This topic builds the stateless end properly. The client logs in once, gets a **signed token**, and sends it on every later request; the server verifies the signature and trusts the claims inside — no session table, no lookup. You'll issue the token, validate it with a custom filter you write by hand, and be able to say exactly what the signature does and does *not* protect.

---

## The problem this solves

A session lives in server memory (or a shared store). That's fine for one server, but the moment you run three instances behind a load balancer, request #2 can land on a different instance than the one that created the session — and that instance has never heard of this user. You either pin users to instances (fragile) or add a shared session store (another moving part).

A token sidesteps this. Everything the server needs to identify the caller — who they are, what they can do, when the token expires — is carried *inside the token*, signed so it can't be forged. Any instance can verify it with the same key and no shared state. This is why stateless tokens are the default for REST APIs, mobile back ends, single-page-app back ends, and service-to-service calls.

It also kills a specific bad idea this roadmap's audience has seen: "doing JWT" by base64-encoding a user id and calling it a token. Base64 is not a signature — anyone can decode it, change the id to `admin`, and re-encode. A real token is *signed*, and that one difference is the whole topic.

---

## How it works

### A JWT is three base64url parts

A JWT is a single string with three dot-separated segments: `header.payload.signature`.

```
eyJhbGciOiJIUzI1NiJ9  .  eyJpc3MiOiJqd3QtZGVtbyIsInN1YiI6ImFsaWNlIiwicm9sZXMiOiJST0xFX1VTRVIiLC4uLn0  .  <signature bytes>
     header (alg)                        payload (the claims)                                                signature
```

- **Header** — names the algorithm, e.g. `{"alg":"HS256"}`.
- **Payload** — the **claims**. Standard ones: `iss` (issuer), `sub` (subject — the username), `iat` (issued-at), `exp` (expiry). Plus anything you add — here a `roles` claim carrying the authorities.
- **Signature** — the header and payload, signed with a secret. This is the only part an attacker can't reproduce.

Decode the payload of a real token from the demo — **no key required**:

```json
{ "iss": "jwt-demo", "sub": "alice", "exp": 1783349068, "iat": 1783345468, "roles": "ROLE_USER" }
```

That's the first lesson: **the payload is readable by anyone who has the token.** A JWT signature provides *integrity* (the claims weren't changed) and *authenticity* (it was issued by whoever holds the secret) — **not confidentiality.** Never put a password, a card number, or anything secret in a JWT payload.

### Issuing: authenticate once, then sign the claims

The login endpoint checks the password *one time* using the same `AuthenticationManager` / `UserDetailsService` / `PasswordEncoder` model from topics 04–05, then hands the result to a service that signs a token:

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("jwt-demo")
        .issuedAt(now)
        .expiresAt(now.plus(1, ChronoUnit.HOURS))   // exp — the token stops working after this
        .subject(authentication.getName())            // sub
        .claim("roles", roles)                         // custom claim: "ROLE_USER" / "ROLE_ADMIN"
        .build();
JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```

After this, the client holds a token and **never sends the password again.** That is the trade: verify expensively once, then trust a cheap signature check on every subsequent request.

### "Self-signed" means one secret does both jobs

This demo uses **HS256** — HMAC with a symmetric secret. The *same* secret produces the encoder that signs and the decoder that verifies:

```java
@Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
@Bean JwtDecoder jwtDecoder(SecretKey key) { return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256).build(); }
```

That works *because the same application issues and validates*. It's the simplest correct setup, and it sets up the next topic by contrast: when **someone else** issues the token (an external identity provider, topic 09), you can't share their secret — which is exactly why real IdPs sign with a *private* key and publish a *public* one. Topic 09's headline, "no shared secret," only makes sense once you've held the shared-secret version in your hands.

### Validating: a custom filter you write by hand

There's no `oauth2ResourceServer` here — topic 08 does the validation manually so you can see every step. A `OncePerRequestFilter` reads the `Authorization: Bearer <token>` header, verifies the token, and if it holds up, tells Spring Security who the caller is:

```java
Jwt jwt = decoder.decode(token);              // verifies signature AND exp; throws JwtException on failure
String username = jwt.getSubject();
var authorities = parseRoles(jwt.getClaimAsString("roles"));
var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
SecurityContextHolder.getContext().setAuthentication(authentication);   // credentials are null — the token IS the proof
```

`decoder.decode(...)` is the security-critical line. It recomputes the signature over `header.payload` and compares it to the signature in the token, and it checks `exp`. A tampered payload or an expired token throws, the context stays empty, and the request is treated as unauthenticated. The filter is inserted **before** `UsernamePasswordAuthenticationFilter` so the context is populated before the authorization rules run.

**Topic 09 deletes this entire filter.** `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` installs an equivalent one for you. Writing it once here is what makes that one-liner comprehensible instead of magic.

### What the demo proves

| Request | Token | Result | Why |
|---------|-------|--------|-----|
| `GET /public/hello` | none | 200 | `permitAll()` |
| `POST /auth/login` (good creds) | — | 200 + `{"token":"…"}` | password checked once, token minted |
| `POST /auth/login` (bad creds) | — | **401** | no token is ever issued for a bad password |
| `GET /api/me` | none | **401** | protected route, no proof of identity |
| `GET /api/me` | valid `alice` | 200 → `{"user":"alice","authorities":["ROLE_USER"]}` | claims became the `Authentication` |
| `GET /api/admin/stats` | valid `alice` (USER) | **403** | authenticated, but the `roles` claim lacks `ROLE_ADMIN` |
| `GET /api/admin/stats` | valid `admin` | 200 | `roles` claim carries `ROLE_ADMIN` |
| `GET /api/me` | **tampered** token | **401** | signature no longer matches |
| `GET /api/me` | **expired** token | **401** | `exp` is in the past |

The `403` row is the payoff: authorization from topic 07 still works, but the role now travels *inside the token* rather than coming from a session or a database lookup.

---

## Vulnerable / broken version

The failure that defines this topic is **trusting a token without verifying its signature.** Suppose the filter decoded the payload but skipped the signature check (or the header said `"alg":"none"` and the server honored it — the classic JWT vulnerability). Then anyone can:

1. Log in as `alice`, get a real token, and read the payload — trivially, it's just base64.
2. Change `"roles":"ROLE_USER"` to `"roles":"ROLE_ADMIN"`, re-encode, and send it.
3. Get admin access, because nothing checked that the signature still matched the (now edited) payload.

The demo proves the fix works: flip a single character in the payload of a valid token and the response is **`401`**, because `decoder.decode(...)` recomputes the HMAC over the edited payload and it no longer equals the signature. The signature is the entire defense. A token library that verifies signatures (and rejects `alg:none`) is non-negotiable — this is why the module says *use a vetted library, never hand-roll it.*

---

## When to use it / when not

- **Use a stateless token** for REST APIs consumed by SPAs, mobile apps, or other services; anywhere you scale horizontally and don't want a shared session store; anywhere one service must forward a caller's identity to another (topic 09 and module 13).
- **Keep server-side sessions** for classic server-rendered web apps where the browser holds a cookie and you want *instant* logout/revocation. A session can be deleted server-side and is dead immediately.
- **The cost of statelessness is revocation.** A signed token is valid until it expires, full stop — there's no server record to delete. If `alice`'s token is stolen, it works until `exp`. You manage this with **short lifetimes** (minutes, then refresh) and, when you truly need instant kill-switches, a denylist of token ids — which quietly reintroduces the server-side state you were avoiding. Choose the lifetime deliberately.

---

## Common pitfalls

- **Not verifying the signature / honoring `alg:none`.** The whole-topic failure above. Always verify; pin the expected algorithm (the decoder here is pinned to `HS256`). A decoder that accepts `none` accepts forgeries.
- **Putting secrets or PII in the payload.** It's base64, not encryption — readable by anyone holding the token. Claims are for identity and authorization, not confidential data.
- **No `exp`, or an `exp` measured in months.** A token with no expiry (or a very long one) is a permanent credential the moment it leaks. Keep access tokens short-lived.
- **Leaking the signing secret.** Anyone with the HMAC secret can forge a valid token for *any* user, including `admin`. Load it from the environment or a secret manager; never commit it (the demo's `application.yml` has a loud placeholder and reads `DEMO_JWT_SECRET`). An HS256 secret must also be ≥ 32 bytes.
- **A wrong-role `403` silently becoming `401` (verified in this demo).** The default `AccessDeniedHandler` calls `response.sendError(403)`, which makes the servlet container re-dispatch to `/error`. That error dispatch re-enters the security chain as **anonymous** (a `OncePerRequestFilter` doesn't re-run on error dispatch), `/error` requires auth, and the entry point overwrites your `403` with a `401`. MockMvc never does the error dispatch, so tests pass while `curl` shows the wrong code. Fix: give the `accessDeniedHandler` a lambda that uses `response.setStatus(403)` (no error dispatch), or `permitAll()` the `/error` path. Topic 12 rebuilds both handlers around `ProblemDetail`.
- **Confusing the two failure codes.** Missing/tampered/expired token → `401` (we can't authenticate you). Valid token, insufficient role → `403` (we know who you are; you may not do this). Sending `401` for a role problem tells the client to log in again, which never fixes it.
- **Storing the token unsafely on the client.** Out of scope for the server, but: a token in `localStorage` is readable by any XSS on the page. This is a real reason cookie-based sessions still exist for browser apps.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/08-jwt-stateless-auth/jwt-demo`. Run it and walk the `curl` table in the demo README.

1. **Read the payload without a key.** Log in, take the token, split on `.`, base64url-decode the middle segment. You'll see your own claims in plaintext — internalize "no confidentiality."
2. **Forge, and watch it fail.** Flip one character in the payload segment and send the result as a bearer token. `401`. That single character broke the signature — which is the point.
3. **Break the defense on purpose.** In `JwtAuthFilter`, replace `decoder.decode(token)` with code that just base64-decodes the payload and reads `sub`/`roles` *without verifying*. Re-run the forge from step 2 with `roles` edited to `ROLE_ADMIN` — now it works, and you've reproduced the real-world JWT vulnerability. Put the real decode back.
4. **Expire it fast.** Change the `expiresAt` in `JwtService` to `now.plus(5, SECONDS)`, get a token, wait six seconds, use it → `401`. Feel what "stateless means you can't un-issue it, only outlive it" costs.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`JwtConfig.java`** — the crypto: one secret → a `JwtEncoder` (signs) and a `JwtDecoder` (verifies). This file *is* "self-signed."
3. **`JwtService.java`** — issuing: turns a verified `Authentication` into signed claims (`sub`, `roles`, `exp`).
4. **`JwtAuthFilter.java`** — validating by hand: read the bearer header, `decode`, populate the `SecurityContext`. The class topic 09 will replace.
5. **`SecurityConfig.java`** — stateless chain, the filter inserted before `UsernamePasswordAuthenticationFilter`, the `AuthenticationManager` for login, and the 401/403 handlers.
6. **`AuthController.java`** — `POST /auth/login`: check credentials once, return a token.
7. **`DemoController.java`** — public / authenticated (`/api/me`) / admin routes, driven entirely by token claims.
8. **`JwtStatelessTest.java`** — 9 cases, including the tamper and expiry proofs.

## Self-check

1. A JWT has three parts. What is in each, and which one can an attacker *not* forge without the secret?
2. Your colleague says "we'll encrypt the user's role by putting it in the JWT." What's wrong with that sentence?
3. Trace what happens, filter by filter, when a request arrives with `Authorization: Bearer <valid-token>`: where does the `Authentication` come from, and where is it stored?
4. `alice` sends a perfectly valid token to an admin-only endpoint. Does she get `401` or `403`, and why? Now she sends an *expired* token to a normal endpoint — which code, and why the difference?
5. This app both issues and validates with one shared secret. Why does that not work once an *external* provider issues the token, and what does that force the provider to use instead? (That's topic 09.)
