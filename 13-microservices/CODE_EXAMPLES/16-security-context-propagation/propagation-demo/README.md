# propagation-demo

The module-12 → module-13 bridge: carry the caller's identity across a service hop.

inventory-service is secured as an OAuth2 resource server (every request needs a valid
JWT). Tokens are HMAC-signed with a shared demo secret so no external identity provider
is needed to run it (production uses asymmetric keys from an IdP's JWK set — module 12,
topic 09).

`TokenPropagationEndToEndTest` (boots the secured service):
- **without a token the downstream rejects** — `401 Unauthorized`; identity didn't travel.
- **propagating the token lets the call through and identity travels** — the call
  succeeds and the response reports `"caller":"alice"` (the JWT subject).

`PropagationInterceptorTest` (the mechanism in isolation):
- **copies the caller's token onto the outbound request** when there is one,
- **sends no Authorization when there's no caller token** — never fabricates identity.

## Run

```bash
mvn -q test        # Tests run: 4, green
```

## The idea

A `ClientHttpRequestInterceptor` on order-service's outbound client reads the caller's
bearer token (in real code from Spring Security's `SecurityContextHolder`) and sets it on
the request to inventory-service. The identity established at the edge flows inward, and
each service can still verify *who* the caller is — instead of trusting anonymous internal
traffic (fallacy 4).
