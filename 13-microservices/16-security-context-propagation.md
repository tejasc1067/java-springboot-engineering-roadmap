# Security Context Propagation

Module 12 secured a single service: a request arrives with a JWT, the resource server validates it, and the code knows who the user is. But in a distributed system the request doesn't stop there — order-service, acting on the user's behalf, calls inventory-service. **Does inventory-service know who the original user is?** By default, no: identity does not travel across a network hop on its own (fallacy 4, "the network is secure"). This topic is how to carry the caller's identity — the security context — from one service to the next, so the whole call chain can authenticate and authorize, not just the edge.

This is the explicit bridge from module 12 into distributed systems; the demo secures a downstream and shows a call failing without the propagated token and succeeding with it.

---

## The problem this solves

The user logs in and gets a JWT. Their request hits the gateway (topic 14), which validates the token, then order-service handles it. order-service now needs to call inventory-service. Two bad options if identity doesn't travel:

1. **inventory-service trusts any internal caller.** "It's an internal call, so it's fine." But *internal is not a security boundary* (fallacy 4): a compromised service, or anything that can reach the network, can now call inventory-service as anyone. And inventory-service has no idea *which user* the operation is for — it can't do per-user authorization or auditing.
2. **inventory-service requires a token, but order-service doesn't send one.** The call just fails with `401` — which is what the demo shows first. Correct security, broken system.

The fix is **propagation**: order-service forwards the caller's credential (the bearer token) on its outbound call, so inventory-service validates the *same* identity and knows the operation is "for user alice." Identity flows the length of the call chain.

## How it works: forward the token on the outbound call

The demo secures inventory-service as a resource server (module 12): `anyRequest().authenticated()` + `oauth2ResourceServer().jwt()`. Its endpoint reports the caller so we can prove identity arrived:

```java
@GetMapping("/api/inventory/{sku}")
public String getStock(@PathVariable String sku, JwtAuthenticationToken auth) {
    return "...\"caller\":\"" + auth.getName() + "\"..."; // the JWT subject
}
```

Call it with no token and it returns `401` — identity didn't travel. Now the caller propagates the token via a request interceptor:

```java
public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
    String authorization = callerAuthorizationHeader.get(); // the caller's bearer token
    if (authorization != null && !authorization.isBlank()) {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization); // forward it
    }
    return execution.execute(request, body);
}
```

Attach that interceptor to order-service's `RestClient` and the outbound call to inventory-service now carries the caller's token. The demo's end-to-end test shows the call succeeding *and* the response reporting `"caller":"alice"` — the identity established at the edge reached the downstream. Crucially, when there's **no** caller token the interceptor sends nothing (and the downstream correctly rejects) — it never fabricates an identity.

In a real order-service, the token source isn't a hardcoded supplier — it reads the current request's credential from Spring Security's `SecurityContextHolder` (the `Jwt`'s token value) so propagation is automatic for every outbound call. Spring Security's OAuth2 client support can also wire this for you; the interceptor here makes the mechanism explicit.

## What to propagate — and the trust question

You have choices about *what* travels, with different trust models:

- **Forward the original user token** (what the demo does). Simple, and every downstream sees the real user and their scopes. The catch: the token must still be *valid* (unexpired) by the time it reaches the last hop, and every service must be a legitimate audience for it. Good for short chains.
- **Token exchange** — order-service exchanges the user's token for a new one scoped to the specific downstream call (the OAuth2 token-exchange flow). More secure (each token is narrowly scoped and audience-restricted) but needs an identity provider that supports it.
- **Service-to-service credentials + a propagated user claim** — the services authenticate to *each other* with their own credentials (mutual TLS, or client-credentials tokens), and the *user* identity rides along as a validated claim/header. Common in service meshes.

Whichever you choose, the non-negotiable rule: **the downstream re-validates.** inventory-service does not trust that "order-service says it's alice" — it validates the token's signature and claims itself (the resource server does this automatically). Propagation carries the *proof* of identity, not a mere assertion; each service verifies that proof. This is defense in depth: even if an attacker reaches inventory-service directly, they still need a valid token.

## Where the security context also needs to go

Identity is one piece of per-request context that must travel; there are others, and they propagate the same way (a header set on the outbound call):

- The **security token** (this topic).
- The **trace / correlation id** (topic 17), so one request across services is one trace.
- Sometimes **tenant id**, **locale**, or a **request deadline**.

These together are the "request context." A common real-world approach bundles their propagation into one place (an interceptor/filter applied to every client) so no outbound call forgets to carry them.

## When to use it / when not to

- **Propagate identity on any call made on behalf of a user** where the downstream needs to know who they are (authorization, auditing, per-user data). This is the normal case for user-initiated request chains.
- **Background / system work** (a scheduled job, an event consumer) has no user token to propagate — it should authenticate with a *service* identity (client-credentials token or mTLS), not a user's. Don't cache and reuse a user token for background work.
- **Never skip downstream validation** because "the call came from inside." Internal calls are still authenticated and authorized; the gateway validating at the edge does not exempt internal services (fallacy 4).
- **Mind token lifetime across long/async chains.** A user token may expire before an async, delayed step runs (topic 07) — for those, use a service identity or re-mint, not a stale user token.

## Common pitfalls

- **Trusting internal traffic.** "It's internal, so no auth needed." A compromised or rogue internal caller then has free rein. Authenticate every hop.
- **Not propagating, then trusting anyway.** Making inventory-service `permitAll()` for internal callers because order-service doesn't send a token. That's the trap above — it opens the service to anyone on the network.
- **Propagating but not re-validating.** Accepting `X-User: alice` as a plain header the downstream trusts. A header is not proof — anyone can set it. Propagate the *signed token* and validate it downstream.
- **Leaking tokens in logs.** Propagated `Authorization` headers ending up in request logs. Redact them (a real concern once you add request logging at the gateway / topic 17).
- **Reusing a user token for background jobs.** A job has no user; give it a service identity. A user token that outlives the user's session (or expires mid-job) is the wrong credential.

---

## Code examples

Runnable demo — `16-security-context-propagation/propagation-demo/`. Read then `mvn test`:

1. `propagation-demo/src/main/java/com/example/propagation/InventoryServiceApplication.java` — the downstream, secured as a resource server; its endpoint reports the caller's identity.
2. `propagation-demo/src/main/java/com/example/propagation/BearerTokenPropagationInterceptor.java` — the mechanism: forward the caller's token on the outbound call, never fabricate one.
3. `propagation-demo/src/test/java/com/example/propagation/TokenPropagationEndToEndTest.java` — no token → `401`; propagated token → `200` and `"caller":"alice"`.
4. `propagation-demo/src/test/java/com/example/propagation/PropagationInterceptorTest.java` — the interceptor copies the token when present, sends nothing when absent.

Builds on module 12 (resource server, JWT); tokens are HMAC-signed here only so the demo runs without an IdP.

## Try this yourself

1. In the end-to-end test, mint a token that's already expired (`expirationTime` in the past) and propagate it. Confirm the downstream returns `401` — the downstream really is validating, not just checking a header is present.
2. Change the interceptor to always send `Authorization: Bearer totally-made-up`. Confirm the downstream rejects it. This is why "propagate the *signed* token and re-validate" matters — a forged/assertion-only identity fails.
3. Sketch how you'd source the token from `SecurityContextHolder` instead of the test's `Supplier`, so every outbound call in a real order-service propagates automatically. What object holds the current request's `Jwt`?

## Self-check

1. Why does securing only the edge (gateway) leave a gap, and what does propagating the user's token to inventory-service fix?
2. inventory-service receives a call with a propagated token. Why must it still validate the token itself rather than trust that order-service already did? What attack does that prevent?
3. A nightly batch job needs to call inventory-service, but there's no logged-in user. What credential should it use, and why is reusing a user's token wrong here?
