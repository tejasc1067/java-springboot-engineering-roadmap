# 12 — Security

Every module before this one assumed a request that reached your controller was *allowed* to be there. Module 09 named `401` and `403` as status codes; module 11 handled *malformed* and *not-found* and *conflicting* requests but stated outright that it "assumes every request is already allowed." This module removes that assumption. It answers two questions your API has been silently ignoring: **who is this caller?** (authentication) and **are they allowed to do this?** (authorization).

By the end you will take an open Spring Boot API — every endpoint reachable by anyone — and put a `SecurityFilterChain` in front of it: some routes public, some requiring a logged-in user, some requiring a specific role. You will store passwords the way production systems do (BCrypt, never plaintext) and be able to show *why* the naive way is broken. You will secure a stateless REST API three ways that real jobs expect — a self-issued JWT, validating tokens issued by an external identity provider (OAuth2 resource server), and "Log in with Google" (OAuth2/OIDC login). You will get CORS and CSRF right instead of copy-pasting `.csrf().disable()` and hoping. And the `401`/`403` cases will finally flow into the RFC 7807 `ProblemDetail` contract you built in module 11 — the same error shape, now for security failures.

## Who this is for

- Anyone who finished modules 09 (REST APIs), 10 (Spring Data JPA), and 11 (exception handling) and has an API that currently trusts every caller.
- Developers who have seen `SecurityFilterChain`, `@PreAuthorize`, or `.csrf().disable()` in a codebase and copied it without knowing what it does or what it just turned off.
- Anyone who stores passwords in a column called `password` as plaintext, or who "does JWT" by base64-encoding a user id and calling it a token.

## What you'll be able to do at the end

- [ ] Explain the difference between authentication (*who are you*) and authorization (*what may you do*), and name the concrete attack that each failure enables.
- [ ] Describe what Spring Security does the moment you add the starter — every endpoint locked, a generated password, HTTP Basic — and where in the request path the `FilterChainProxy` sits.
- [ ] Write a `SecurityFilterChain` bean that makes some routes `permitAll()` and others `authenticated()`, and explain why order and specificity matter.
- [ ] Explain the `Authentication` / `UserDetails` / `UserDetailsService` / `PasswordEncoder` model, and back it with users from a database instead of the in-memory default.
- [ ] Show a plaintext / MD5 password store being dumped and reversed, then fix it with BCrypt, and explain salting and work factor.
- [ ] Choose between HTTP Basic, form login, and a stateless token, and explain what a session (`JSESSIONID`) buys you and what it costs a REST API.
- [ ] Enforce authorization two ways — URL-based with `authorizeHttpRequests` and method-level with `@PreAuthorize`/`@PostAuthorize` — and explain roles vs. authorities.
- [ ] Issue and validate a self-signed JWT with a custom filter, and explain what the signature does and does *not* protect.
- [ ] Configure `oauth2ResourceServer` to validate JWTs minted by an external identity provider, using its JWK set — no shared secret.
- [ ] Wire up `oauth2Login` (the OIDC authorization-code flow) so users sign in with an external provider, and explain the flow step by step.
- [ ] Explain what CORS and CSRF actually are, when disabling CSRF is correct (a stateless token API) and when it is a serious bug (a cookie/session app).
- [ ] Turn a security `401` (unauthenticated) and `403` (forbidden) into the module 11 `ProblemDetail` shape via an `AuthenticationEntryPoint` and an `AccessDeniedHandler`.
- [ ] Test security with `spring-security-test` — assert that a protected route returns `401` anonymously, `403` for the wrong role, and `200` with `@WithMockUser`.

If you can do all thirteen, the module worked.

## Prerequisites

- **Module 09 done.** You need `@RestController`, request mapping, `ResponseEntity`, and the status-code decision tree — especially what `401` and `403` *mean*. This module supplies the machinery module 09 only named.
- **Module 11 done.** Topic 12 routes security failures into the `@RestControllerAdvice` + `ProblemDetail` contract from module 11. You should already have that error shape in your head.
- **Module 10 helpful.** Topics 04–05 back the user store with a JPA `UserRepository` and H2. You can follow with an in-memory user store, but the database-backed version is closer to real.
- A JDK installed (Java 17 or newer; examples target Java 21).
- For topics 09–10 (OAuth2), an external identity provider. The demos use a free one (a local Keycloak container or a Google OAuth client) — each topic's README gives the exact setup so you never need a paid account.

## How this module is organized

Topic 01 is markdown-only. It sets the threat model — what an unauthenticated, unauthorized API actually exposes — and names the goal of the module. Without seeing what "open" costs you, the filter config is just annotations.

Topics 02–03 are **the mechanism**: what the starter does by default (topic 02), then the `SecurityFilterChain` bean you write to take control (topic 03).

Topics 04–06 are **authentication** — the model (topic 04), storing passwords correctly with the vulnerable-then-safe contrast (topic 05), and choosing a login style: Basic vs. form vs. stateless (topic 06).

Topic 07 is **authorization** — URL rules and method security, roles vs. authorities.

Topics 08–10 are the three **stateless / delegated** patterns real APIs use, in increasing delegation: a JWT you sign yourself (topic 08), a JWT signed by someone else that you only validate (topic 09, resource server), and full delegated login where the provider authenticates the user for you (topic 10, OAuth2/OIDC login).

Topic 11 is **CORS and CSRF** — the two browser-security mechanisms people most often misconfigure. Topic 12 connects security failures to module 11's **error contract**. Topic 13 is **testing** security so a broken rule fails a test instead of shipping.

```text
01-why-security-auth-vs-authz         <- threat model; auth vs authz; the goal; markdown-only
02-spring-security-defaults           <- add the starter, everything locks, generated password, the filter chain
03-securityfilterchain-config         <- the SecurityFilterChain bean, permitAll vs authenticated, order
04-authentication-model               <- Authentication, UserDetails(Service), DB-backed users
05-password-storage                   <- plaintext/MD5 dumped and reversed, then BCrypt (vulnerable -> safe)
06-form-login-vs-basic-vs-stateless   <- sessions, JSESSIONID, when each login style fits
07-authorization                      <- authorizeHttpRequests + @PreAuthorize/@PostAuthorize, roles vs authorities
08-jwt-stateless-auth                 <- issue + validate a self-signed JWT, custom filter, what the signature protects
09-oauth2-resource-server             <- validate tokens from an external IdP via its JWK set, no shared secret
10-oauth2-login                       <- the OIDC authorization-code flow, "Log in with Google", step by step
11-cors-and-csrf                      <- what each is, when disabling CSRF is correct and when it is a bug
12-401-403-error-contract             <- AuthenticationEntryPoint + AccessDeniedHandler -> module 11 ProblemDetail
13-testing-security                   <- spring-security-test, @WithMockUser, asserting 401/403/200
```

```text
COMMON_MISTAKES.md       <- real security bugs with code: leaked secrets, permitAll on actuator, blind csrf-disable, key exposure
INTERVIEW_QNA.md         <- interview-grade questions on the filter chain, sessions vs tokens, CSRF, BCrypt, OAuth2 flows
```

## The toolkit (what we add to the pom)

Module 12 stays on the same Spring Boot version as modules 08–11 so projects move between modules without surprises. The core new starter is `-security`; the OAuth2 topics add two more, and testing adds `spring-security-test`.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.14` | parent POM; locks versions | every topic |
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | `@RestController`, MVC, embedded Tomcat | every topic |
| Spring Boot Starter Security | `org.springframework.boot:spring-boot-starter-security` | the `FilterChainProxy`, `SecurityFilterChain`, `@PreAuthorize`, `PasswordEncoder` | topics 02+ |
| Spring Security Test | `org.springframework.security:spring-security-test` | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` | topics 03+ (tests) |
| Spring Boot Starter Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa` | JPA-backed `UserDetailsService` | topic 04 |
| H2 Database | `com.h2database:h2:2.2.224` | in-memory user store | topic 04 |
| Spring Boot Starter OAuth2 Resource Server | `org.springframework.boot:spring-boot-starter-oauth2-resource-server` | validate incoming JWTs against a JWK set (Nimbus) | topic 09 |
| Spring Boot Starter OAuth2 Client | `org.springframework.boot:spring-boot-starter-oauth2-client` | `oauth2Login`, the authorization-code flow | topic 10 |

A note on JWT libraries: topic 08 (self-signed JWT) uses the `spring-security-oauth2-jose` `JwtEncoder`/`JwtDecoder` that the resource-server starter already brings in, rather than adding a third-party JWT library — one fewer dependency to explain, and it's the same Nimbus code the framework uses.

## How to run the examples

Each topic with a demo is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To run one:

```bash
cd 12-security/CODE_EXAMPLES/03-securityfilterchain-config/filterchain-demo
mvn -q test                       # runs the security assertions (anonymous 401, wrong-role 403, ok 200)
mvn -q spring-boot:run            # boots the app on :8080 so you can curl it
```

Every demo's README lists the exact `curl` commands — including how to send credentials and a bearer token — so you can *see* the `401` / `403` / `200` yourself. For the OAuth2 topics, the README gives the one-command identity-provider setup (a local Keycloak container, or a Google OAuth client) so the flow runs end-to-end on your machine.

## Checkpoint

You're done with this module when you can:

1. Be shown an open Spring Boot API and add a `SecurityFilterChain` that makes `/public/**` open, `/api/**` require a logged-in user, and `/admin/**` require `ROLE_ADMIN` — and explain what each caller gets when the rule is violated.
2. Explain the `UserDetailsService` / `PasswordEncoder` handshake that happens when a user submits a password, and why the stored value is a BCrypt hash, not the password.
3. Be shown a table of plaintext passwords, describe the exact damage a leak causes (credential stuffing across other sites), and explain how BCrypt's salt and work factor change the attacker's economics.
4. State when you would keep server-side sessions and when you would go stateless with a token — and what each choice implies for horizontal scaling and CSRF.
5. Issue a JWT, then explain what its signature guarantees (integrity, issuer) and what it does *not* (confidentiality — the payload is readable by anyone).
6. Explain the difference between a resource server (topic 09) validating a token and OAuth2 login (topic 10) obtaining one, and draw the authorization-code flow.
7. Say when `.csrf().disable()` is the correct configuration and when it opens a real vulnerability, tied to whether the app authenticates with a cookie or a bearer token.
8. Make a security `403` come back as the same `ProblemDetail` shape as a validation `400` from module 11.

If those eight feel comfortable, move to module 13 (Microservices), where these single-service auth patterns meet service-to-service calls and token propagation.

## A note on what's *not* here

- **No custom cryptography.** You will use BCrypt and validate signatures with a library. Writing your own crypto is the canonical security mistake; this module teaches you to reach for vetted primitives, not to build them.
- **No full OAuth2 authorization-server build.** You will *use* an identity provider (topics 09–10) and validate its tokens; you will not build the provider itself. Running your own authorization server (Spring Authorization Server) is a specialized task beyond a backend fundamentals roadmap.
- **No SAML, LDAP, or Kerberos.** Enterprise SSO protocols are real but niche for someone learning backend fundamentals; OAuth2/OIDC is the modern default and the one worth your time first.
- **No secrets-management infrastructure.** The module says *don't hardcode secrets* and reads them from configuration/environment, but Vault, AWS Secrets Manager, and KMS are cloud/ops topics the roadmap deliberately leaves out.
- **No rate limiting, WAF, or DDoS defense.** These are edge/infra concerns, not application authentication and authorization. Some are touched on in module 19 (observability) and belong to a separate ops roadmap.
- **No reactive / WebFlux security.** The roadmap is servlet-stack throughout; the reactive `SecurityWebFilterChain` is analogous but out of scope.
</content>
</invoke>
