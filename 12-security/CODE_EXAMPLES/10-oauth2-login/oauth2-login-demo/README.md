# 10 — OAuth2 / OIDC login ("Log in with Google")

The app is an OAuth2 **client**: it logs the user in through an external provider and reads their identity. Opposite role from topic 09 (which validated a token it received). This is a **stateful** browser app — login creates a session, and CSRF stays on.

- `GET /` — public.
- `GET /api/me` — protected; after login returns the OIDC `subject`, `email`, and `name`.
- `oauth2Login()` in `SecurityConfig` drives the whole authorization-code flow.

It **boots offline** with placeholder credentials so you can watch the flow start; completing a real login needs a provider (below).

## Run the tests

```bash
mvn -q test
```

`OAuth2LoginTest` (4 cases): home is public; an anonymous protected route redirects into the flow; `/oauth2/authorization/google` builds Google's authorization URL with `response_type=code`, `client_id`, `scope=openid…`, `state`, `redirect_uri`; and a simulated OIDC login (`oidcLogin()`) exposes the identity on `/api/me`.

## Watch the flow start (offline, no provider needed)

```bash
mvn -q spring-boot:run
```

```bash
# Anonymous protected route -> redirected INTO the flow (a 302, not a 401 — this is a browser app)
curl -i http://localhost:8080/api/me
#   HTTP/1.1 302   Location: http://localhost:8080/oauth2/authorization/google

# The authorization endpoint -> redirect to Google with the authorization-code parameters
curl -i http://localhost:8080/oauth2/authorization/google
#   HTTP/1.1 302
#   Location: https://accounts.google.com/o/oauth2/v2/auth
#     ?response_type=code
#     &client_id=demo-client-id
#     &scope=openid%20profile%20email
#     &state=<random>            <- CSRF protection for the flow
#     &nonce=<random>            <- OIDC id_token replay protection
#     &redirect_uri=http://localhost:8080/login/oauth2/code/google
```

With placeholder credentials Google will reject the actual sign-in (unknown `client_id`) — but you can *see* the app construct the exact request. That's steps 1–3 of the authorization-code flow.

## Complete a real login — Option A: Google

1. Google Cloud Console → **APIs & Services → Credentials → Create OAuth client ID → Web application**.
2. Add **Authorized redirect URI**: `http://localhost:8080/login/oauth2/code/google` (must match exactly).
3. Copy the client id and secret into env vars and run:

```bash
export GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=yyyy
mvn -q spring-boot:run
```

4. Open `http://localhost:8080/api/me` in a **browser**, sign in with Google, and you'll land back on `/api/me` showing your email. (Use a browser, not curl — the flow is interactive redirects.)

## Complete a real login — Option B: Keycloak (fully local, no Google account)

```bash
docker run --rm -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:25.0 start-dev
```

In the admin console (`http://localhost:8081`, admin/admin): create realm `demo`, a **Confidential** client (Standard flow on) with redirect URI `http://localhost:8080/login/oauth2/code/keycloak`, and a user with a password. Then swap the `google` registration in `application.yml` for a `keycloak` one that uses discovery:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: <your-client>
            client-secret: <your-secret>
            scope: openid, profile, email
        provider:
          keycloak:
            issuer-uri: http://localhost:8081/realms/demo   # discovery fetches all endpoints + JWK set
```

Open `http://localhost:8080/api/me`, sign in via Keycloak, and read your identity.

## Notes

- Depends on `spring-boot-starter-oauth2-client` (topic 10's starter) — the OAuth2 **client** side, not the resource-server side from topic 09.
- Because Google is an OIDC provider and we request the `openid` scope, the principal is an `OidcUser` (built from the id_token). Drop `openid` and it becomes a plain `OAuth2User`.
- The `client-secret` is a real credential — it's read from `GOOGLE_CLIENT_SECRET`, never committed. The `application.yml` placeholder only exists so the app boots offline.
- Registration id `google` is a built-in provider: Spring supplies Google's endpoint URLs, so no `provider:` block is needed (Keycloak, being custom, uses `issuer-uri` discovery).
