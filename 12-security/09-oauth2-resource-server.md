# OAuth2 resource server — validate tokens someone else issued

Topic 08 built an app that both *issued* and *validated* tokens with one shared secret. Real systems split those jobs: a central **identity provider** (IdP) — Keycloak, Auth0, Okta, Google, your company's SSO — issues tokens, and each of your services merely *validates* them. A service in that validating role is a **resource server**. This topic configures one: it accepts a bearer token it did not create, verifies it against the IdP's **public** key, and authorizes by the token's **scopes** — all without holding any secret capable of minting a token.

---

## The problem this solves

You have five microservices and one login system. You do not want each service to store passwords, run its own login form, or share a signing secret — that's five copies of your most sensitive credential, five things to rotate, five ways to leak it. Instead one IdP authenticates users and issues signed tokens; every service trusts tokens from that IdP by checking the signature against the IdP's **published public key**. No service can forge a token (none has the private key), and rotating the key is the IdP's job, picked up automatically.

This is the middle rung of the delegation ladder the module walks: **topic 08** you signed tokens yourself; **topic 09** you validate tokens signed by someone else; **topic 10** that someone else even runs the login for you.

---

## How it works

### Asymmetric keys are the whole difference from topic 08

Topic 08 used HS256 — one symmetric secret that both signs and verifies. That's fine when one app does both, but a verifier holding the signing secret *can also forge tokens*. A resource server must not be able to do that. So IdPs use **asymmetric** signing (RS256): a **private** key signs, and the matching **public** key verifies. The IdP keeps the private key; it publishes the public key to the world. Your resource server downloads the public key and can verify signatures forever — but can never produce one. That's the literal meaning of "no shared secret": the two ends hold *different* keys.

In this demo the app is configured with a public key and nothing else:

```yaml
spring.security.oauth2.resourceserver.jwt.public-key-location: classpath:app.pub
```

There is no `JwtEncoder`, no `JwtService`, no signing key anywhere in the app. It can verify; it cannot mint. The demo's *test tree* holds the matching private key and plays the IdP — which is exactly where a signing key belongs: with the issuer, never with the verifier.

### The JWK Set: how the public key travels

In production you don't hand-copy a public key file. The IdP publishes a **JWK Set** (JSON Web Key Set) — a JSON document at a well-known URL listing its current public keys:

```json
{ "keys": [
  { "kty": "RSA", "kid": "a1b2c3", "use": "sig", "alg": "RS256", "n": "0vx7…<base64url modulus>…", "e": "AQAB" }
]}
```

Each token's header names a `kid` (key id); the server fetches the JWK Set, finds the key with the matching `kid`, and verifies with it. When the IdP rotates keys, it publishes new ones and the server picks them up on the next fetch — no redeploy. You point Spring at the IdP and it discovers all of this:

```yaml
# validates the signature AND that the token's "iss" claim matches this issuer
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8081/realms/demo
```

Given `issuer-uri`, Spring reads `{issuer}/.well-known/openid-configuration`, finds the `jwks_uri`, and fetches the keys. (`public-key-location`, used here so the demo runs offline, is the "pin one static key" shortcut — it verifies the signature and `exp`, but does *not* check `iss`. Prefer `issuer-uri` in real systems.)

### One line replaces topic 08's entire filter

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

That installs the framework's `BearerTokenAuthenticationFilter`, which does everything topic 08's hand-written `JwtAuthFilter` did — read the `Authorization: Bearer` header, decode and verify the token, populate the `SecurityContext` — and more (a spec-compliant `WWW-Authenticate` header, standard error codes). **Topic 08's filter existed so this line wouldn't be magic.** Delete the filter; keep the understanding.

After validation the principal is the decoded token itself, a `Jwt`, so a controller can read its claims directly:

```java
@GetMapping("/api/me")
public Map<String,Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("subject", jwt.getSubject(), "scopes", jwt.getClaimAsString("scope"));
}
```

### Authorize by scope

OAuth2 tokens carry a `scope` claim — a space-delimited list of what the token is permitted to do (`books:read books:write`). Spring maps each scope to a `SCOPE_<name>` authority automatically, so URL rules read naturally:

```java
.requestMatchers(HttpMethod.GET,    "/api/books/**").hasAuthority("SCOPE_books:read")
.requestMatchers(HttpMethod.DELETE, "/api/books/**").hasAuthority("SCOPE_books:write")
```

Scopes describe what *the token* may do (granted at issue time by the IdP), which is subtly different from topic 07's roles describing what *the user* is. If your IdP puts roles in a custom claim instead, you swap in a `JwtAuthenticationConverter` to map that claim — the authorization rules don't change.

### What the demo proves

| Request | Token | Result | Why |
|---------|-------|--------|-----|
| `GET /public/hello` | none | 200 | `permitAll()` |
| `GET /api/me` | none | **401** | no bearer token (`WWW-Authenticate: Bearer`) |
| `GET /api/me` | valid | 200 → subject, issuer, scopes | claims survived verification |
| `GET /api/books` | scope `books:read` | 200 | `SCOPE_books:read` present |
| `DELETE /api/books/1` | scope `books:read` | **403** | valid token, but lacks `SCOPE_books:write` (`insufficient_scope`) |
| `DELETE /api/books/1` | scope `books:read books:write` | 200 | scope present |
| `GET /api/books` | **expired** | **401** | `exp` in the past |
| `GET /api/books` | **signed by another key** | **401** | signature fails against the app's public key — can't be forged |

The last row is the point of asymmetric keys: a well-formed token with perfect claims is still rejected unless it was signed by the *one* private key whose public half the app trusts.

---

## Vulnerable / broken version

A resource server that verifies the signature but **ignores the `aud` (audience) claim** has a real hole. IdPs routinely issue one token a client can present to several services. If your `orders` service doesn't check that the token was actually *meant for it*, a token a caller legitimately obtained for the `reports` service (same IdP, valid signature, not expired) is accepted by `orders` too. The signature check passes — it's a genuine token from a trusted issuer — but it was never authorized for this service.

The fix is to validate audience in addition to the signature:

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefault(),                              // signature, exp, nbf
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("orders-service"))));
    return decoder;
}
```

Signature + issuer + audience + expiry together are what "this token is valid *for me*" means. Signature alone only means "this token is genuine."

---

## When to use it / when not

- **Use a resource server** whenever an IdP already issues tokens — SSO across multiple services, any API fronted by Keycloak/Auth0/Okta/Entra. You validate; you never store passwords or run login. This is the default for microservices (module 13 builds on it: one service forwards its caller's token to the next).
- **Prefer it over topic 08's self-issued tokens** once more than one service is involved. Self-signing with a shared HMAC secret means every service holds a forging key — resource-server + asymmetric keys removes that entirely and hands key rotation to the IdP.
- **Not for interactive browser login.** A resource server validates a token the client already has; it does not *obtain* one or show a login page. Logging a user in via the provider is OAuth2 **login** (topic 10). A single app can be both.
- **Overkill for a lone service with no IdP.** If you have one service and no identity provider, topic 08's self-issued token is simpler. Don't stand up Keycloak to protect one endpoint.

---

## Common pitfalls

- **Not validating `aud`.** The broken case above. A valid signature means "genuine," not "meant for this service." Check audience (and issuer).
- **Sharing a symmetric secret with the IdP.** If you configure the resource server with the *same* secret the IdP signs with, you've thrown away the entire point — every service can now forge tokens, and the secret is copied everywhere. Use the IdP's public JWK Set.
- **Confusing scopes and roles.** OAuth2 tokens carry `scope` → Spring makes `SCOPE_*` authorities, so use `hasAuthority("SCOPE_x")` (or `scope`-aware rules), *not* `hasRole`. If the IdP sends roles in a custom claim, add a `JwtAuthenticationConverter` — `hasRole` won't find them otherwise.
- **`public-key-location` in production.** Fine offline, but it pins one key and skips the `iss` check. Real deployments use `issuer-uri` so key rotation and issuer validation happen for free.
- **Clock skew false-failures.** If the IdP's and the server's clocks differ, `exp`/`nbf` checks can spuriously fail. Nimbus allows 60s skew by default; don't set it to zero, and keep clocks in sync (NTP).
- **Token in the URL.** Putting the token in a query parameter leaks it into access logs, browser history, and referrer headers. It goes in the `Authorization: Bearer` header, nowhere else.
- **Mistaking a resource server for a client.** This app has no login page and no client secret — it never talks to the IdP to *get* a token, only to fetch public keys. Obtaining tokens on a user's behalf is topic 10.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/09-oauth2-resource-server/resource-server-demo`. It runs fully offline: the app holds only the public key; the test tree mints tokens with the matching private key.

1. **Run the tests** (`mvn test`) — 9 cases including expired and foreign-key-signed tokens rejected with `401`.
2. **Get a token and curl it.** `mvn -q test -Dtest=PrintToken` prints two ready-to-use bearer tokens (read-only and read+write). Boot the app (`mvn spring-boot:run`) and walk the `curl` table in the demo README. Watch the read-only token get `200` on `GET /api/books` and `403` on `DELETE`.
3. **Decode the payload.** Base64url-decode the token's middle segment — still readable, no key needed (same lesson as topic 08). Notice the `scope` claim; that's what the `403` hinges on.
4. **Forge and fail.** Flip a character in the token and re-send → `401`. The signature check is doing its job against the public key.
5. **Move a rule.** Change `DELETE /api/books/**` to require `SCOPE_books:read` and re-run — now the read-only token can delete. Feel how the IdP's scopes, not your code, decide what a token may do.
6. **(Optional, needs Docker)** Comment out `public-key-location`, set `issuer-uri` to a local Keycloak realm, and fetch a real token from its `/token` endpoint. See the demo README for the container command.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`application.yml`** — the `public-key-location` (offline) config, with the production `issuer-uri` form documented alongside. This file is where "we only hold a public key" lives.
3. **`SecurityConfig.java`** — `oauth2ResourceServer(...jwt())` and the scope-based URL rules. Compare its size to topic 08's config: no key config, no service, no filter.
4. **`DemoController.java`** — reads claims off the `@AuthenticationPrincipal Jwt`; scope-gated read and delete.
5. **`TokenIssuer.java`** *(test)* — stands in for the IdP: holds the private key, signs RS256 tokens (normal, expired, foreign-key).
6. **`OAuth2ResourceServerTest.java`** *(test)* — 9 cases sending real signed tokens as bearer headers, so the real signature check runs.
7. **`PrintToken.java`** *(test)* — prints a token for the manual `curl` walkthrough without a running IdP.

## Self-check

1. This app validates tokens just like topic 08's did. Name the two things it deliberately does *not* do that topic 08's app did.
2. Why can this resource server verify a token but never forge one? Which key does it hold, and which key does the IdP keep?
3. What is a JWK Set, where does the server get it, and how does it choose which key to verify a given token with?
4. A caller sends a valid, unexpired, correctly-signed token and still gets `403`. What's the cause in this demo — and why is it `403` and not `401`?
5. The signature verifies fine, so the token is genuine. Why might you *still* reject it? (Name the claim.)
