# Common mistakes — Security

Real security bugs from real Spring Boot codebases, each with the broken code, what goes wrong, and the fix. Every one of these ships to production regularly — and unlike a validation slip, these fail *silently* or *dangerously*: the app boots, the happy path works, and the hole is invisible until someone finds it.

---

## 1. Storing passwords in plaintext (or with a fast hash)

```java
user.setPassword(rawPassword);                 // plaintext
// or barely better:
user.setPassword(DigestUtils.md5Hex(rawPassword));   // fast, unsalted hash
```

**What goes wrong:** when the users table leaks — and databases leak — plaintext passwords are instantly usable everywhere the victim reused them (credential stuffing). MD5/SHA-256 are no defense: they're *fast*, so an attacker hashes billions of guesses per second against a GPU, and unsalted hashes fall to precomputed rainbow tables in seconds. "We hashed them" is not the same as "they're safe."

**Fix:** a slow, salted, adaptive hash — BCrypt. It bakes a per-password random salt into the output and a tunable work factor, so each guess is deliberately expensive.

```java
@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
// store: encoder.encode(rawPassword);   verify: encoder.matches(raw, stored);
```

---

## 2. Copy-pasting `.csrf(csrf -> csrf.disable())` into a cookie/session app

```java
http.formLogin(withDefaults())
    .csrf(csrf -> csrf.disable());   // "the POST kept getting 403, so I turned it off"
```

**What goes wrong:** the app authenticates with a `JSESSIONID` cookie, which the browser attaches **automatically** to every request to your domain. With CSRF off, a page on `evil.com` can auto-submit a form to `your-app.com/transfer`, the browser attaches the victim's session cookie, and the action runs as them. The `403` you "fixed" was the protection working.

**Fix:** keep CSRF on whenever the credential is browser-auto-sent (cookies, HTTP Basic, a token in a cookie). Disabling it is correct *only* for a stateless API authenticated by a bearer token in the `Authorization` header, which a cross-site page cannot set.

```java
http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
```

---

## 3. `@PreAuthorize` silently ignored because `@EnableMethodSecurity` is missing

```java
@Configuration
@EnableWebSecurity           // present...
public class SecurityConfig { ... }   // ...but no @EnableMethodSecurity

@DeleteMapping("/books/{id}")
@PreAuthorize("hasRole('ADMIN')")     // looks protected
public void delete(@PathVariable Long id) { ... }
```

**What goes wrong:** without `@EnableMethodSecurity`, `@PreAuthorize` is **not evaluated at all**. The annotation is inert decoration; `delete` runs for every authenticated user — including a plain `USER`. Nothing errors. The endpoint looks locked and is wide open.

**Fix:** enable it, and always test the *denied* case so the silent failure fails a test instead of shipping.

```java
@Configuration @EnableWebSecurity @EnableMethodSecurity
public class SecurityConfig { ... }
```

---

## 4. `hasRole("ROLE_ADMIN")` — the double prefix

```java
.requestMatchers("/api/admin/**").hasRole("ROLE_ADMIN")   // wrong
```

**What goes wrong:** `hasRole(x)` prepends `ROLE_` for you, so `hasRole("ROLE_ADMIN")` checks for the authority `ROLE_ROLE_ADMIN`, which nobody has. Every admin gets `403`, forever. The mirror bug — `hasAuthority("ADMIN")` when the authority is actually `ROLE_ADMIN` — silently lets no one (or the wrong people) in.

**Fix:** `hasRole("ADMIN")` **or** `hasAuthority("ROLE_ADMIN")`, never `hasRole("ROLE_ADMIN")`. Pick one vocabulary and be consistent.

---

## 5. `permitAll()` on the actuator (or any broad path)

```java
.requestMatchers("/actuator/**").permitAll()   // "so the health check works"
```

**What goes wrong:** `/actuator/**` is far more than `/health`. Depending on what's exposed, anonymous users can read `/actuator/env` (config, sometimes secrets), `/actuator/beans`, `/actuator/mappings`, or trigger `/actuator/heapdump` (a full memory dump, including credentials in RAM). One `permitAll` glob turned an ops convenience into an information-disclosure endpoint.

**Fix:** permit only what's genuinely public and lock the rest behind a role.

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

---

## 6. `authorizeHttpRequests` rules in the wrong order

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/**").permitAll()                       // matches everything first...
    .requestMatchers("/api/admin/**").hasRole("ADMIN"));      // ...so this is never reached
```

**What goes wrong:** rules are evaluated top-to-bottom, **first match wins**. `/**` matches `/api/admin/anything`, so `permitAll` fires and the `hasRole('ADMIN')` line is dead code. The admin area is open to everyone. It looks protected because the rule is *right there* — it's just unreachable.

**Fix:** order from most specific to most general; put the catch-all last.

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/public/**").permitAll()
.anyRequest().authenticated()
```

---

## 7. Committing the signing secret / client secret

```yaml
# application.yml, checked into git
demo.jwt.secret: s3cr3t-signing-key
spring.security.oauth2.client.registration.google.client-secret: GOCSPX-abc123
```

**What goes wrong:** a JWT signing secret lets anyone who has it **forge a valid token for any user, including admin** — no login required. An OAuth client secret lets anyone **impersonate your application** to the identity provider. Once it's in git history, rotating the deployed value isn't enough; the old one is in every clone and fork forever.

**Fix:** read secrets from the environment or a secret manager; keep only a clearly-marked local placeholder in the file.

```yaml
demo.jwt.secret: ${DEMO_JWT_SECRET:local-dev-only-change-me}
```

---

## 8. Trusting a JWT without verifying its signature (or honoring `alg:none`)

```java
// decode the payload and trust it — NO signature check
String json = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
String role = new ObjectMapper().readTree(json).get("roles").asText();
```

**What goes wrong:** the payload is just base64 — an attacker logs in, decodes their own token, changes `"roles":"ROLE_USER"` to `"ROLE_ADMIN"`, re-encodes, and sends it. Nothing checked that the signature still matches the edited payload, so they're now admin. The historic `alg:none` attack is the same hole: a token header claiming "no algorithm" that a naive verifier accepts as valid-because-unsigned.

**Fix:** verify with a vetted decoder that checks the signature and **pins the expected algorithm**. Never hand-parse a token you intend to trust.

```java
JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
Jwt jwt = decoder.decode(token);   // throws on a tampered payload or a wrong/absent algorithm
```

---

## 9. Putting secrets or PII in a JWT payload

```java
JwtClaimsSet.builder().subject(user).claim("ssn", user.ssn()).claim("cardNumber", card).build();
```

**What goes wrong:** a JWT signature provides *integrity*, not *confidentiality*. The payload is base64, readable by anyone who has the token — the browser, any script on the page, anything that logs the `Authorization` header. You've published the SSN to everyone who can see the token.

**Fix:** claims are for identity and authorization (`sub`, `roles`, `scope`), never confidential data. If the client needs sensitive data, fetch it from an authenticated endpoint — don't ship it in the token.

---

## 10. A resource server sharing the IdP's symmetric secret instead of using its public key

```yaml
# each microservice configured with the SAME HMAC secret the IdP signs with
app.jwt.hmac-secret: ${SHARED_IDP_SECRET}
```

**What goes wrong:** now every service that can *verify* a token can also *forge* one, and the single most sensitive secret in the system is copied into a dozen deployments and CI configs. One leaked service leaks the ability to mint tokens for the whole estate.

**Fix:** the IdP signs with a **private** key (RS256) and publishes the matching **public** key as a JWK Set. Each resource server verifies with the public key and holds no signing capability at all.

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://idp.example.com/realms/app
```

---

## 11. A resource server that doesn't validate the audience (`aud`)

```java
// default validators: signature, expiry, and issuer — but NOT audience
NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
```

**What goes wrong:** an IdP often issues one token a client can present to several services. A caller who legitimately obtained a token for the `reports` service can replay it against `orders` — the signature, expiry, and issuer all check out, so `orders` accepts a token that was never meant for it. A valid, trusted-issuer token still isn't necessarily *meant for you*.

**Fix:** add an `aud` (audience) validator on top of the defaults.

```java
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
    JwtValidators.createDefaultWithIssuer(issuer),
    new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("orders-service"))));
```

---

## 12. `allowedOrigins("*")` together with `allowCredentials(true)`

```java
config.setAllowedOrigins(List.of("*"));
config.setAllowCredentials(true);   // "any origin, and send cookies too"
```

**What goes wrong:** this asks the browser to let *any* website make credentialed cross-origin requests and read the responses using the victim's session. The spec forbids literal `*` with credentials (Spring throws at runtime), so people "fix" it by reflecting the request's `Origin` header back — which is the same hole in disguise: every origin is now trusted.

**Fix:** enumerate the exact origins that may send credentials.

```java
config.setAllowedOrigins(List.of("https://app.example.com"));
config.setAllowCredentials(true);
```

---

## 13. `response.sendError()` in a security handler turns a 403 into a 401

```java
.accessDeniedHandler((req, res, ex) -> res.sendError(403))   // or the default handler, which also uses sendError
```

**What goes wrong:** `sendError` makes the servlet container re-dispatch to `/error`. That dispatch re-enters the security chain, but a custom auth filter doesn't re-run on an error dispatch, so the request is now **anonymous**; if `/error` requires authentication, the entry point fires and overwrites your `403` with a `401`. Worse, MockMvc doesn't perform the `/error` dispatch, so your test sees `403` while production returns `401`.

**Fix:** write the response directly with `setStatus` (no dispatch), or `permitAll()` the `/error` path.

```java
.accessDeniedHandler((req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value()));
```

---

## 14. `new ObjectMapper()` in a security handler → malformed `ProblemDetail`

```java
new ObjectMapper().writeValue(response.getWriter(), problemDetail);   // hand-built mapper
```

**What goes wrong:** Boot's configured `ObjectMapper` has a `ProblemDetail` Jackson mixin (so `type`/`title`/`status`/`detail`/`instance` and custom members serialize *flat*) and the JavaTime module (so an `Instant` becomes an ISO string). A hand-built mapper has neither, so your `401`/`403` body comes out with a nested `properties` object and a raw timestamp array — a different shape from every other error in your API.

**Fix:** inject the Spring-managed `ObjectMapper` bean and use it; also set UTF-8 on the response (`getWriter()` defaults to ISO-8859-1).

---

## 15. Confusing 401 and 403

```java
// wrong-role user gets sent here
.accessDeniedHandler((req, res, ex) -> res.setStatus(401));
```

**What goes wrong:** `401 Unauthorized` means "we don't know who you are — (re)authenticate." Returning it to an authenticated user who merely lacks a role tells the client to log in again, which will *never* grant the role — an infinite, useless retry loop. The reverse (sending `403` to an anonymous caller) hides that they simply needed to log in.

**Fix:** `401` for missing/invalid credentials (the `AuthenticationEntryPoint`), `403` for authenticated-but-forbidden (the `AccessDeniedHandler`). Keep them in the right handler.

---

## 16. `SessionCreationPolicy.STATELESS` on an OAuth2 login app

```java
http.oauth2Login(withDefaults())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

**What goes wrong:** the authorization-code flow stores `state` and `nonce` in the session *between* the redirect to the provider and the callback. With `STATELESS` there's no session to hold them, so the callback fails to correlate the response — typically `authorization_request_not_found`. Someone copied `STATELESS` from a token-API config into an interactive-login app.

**Fix:** interactive login is stateful — let it use a session. Reserve `STATELESS` for bearer-token APIs (topics 08–09), not `oauth2Login` (topic 10).
