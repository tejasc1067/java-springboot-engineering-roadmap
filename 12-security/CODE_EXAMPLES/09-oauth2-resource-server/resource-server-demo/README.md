# 09 — OAuth2 resource server (validate an externally-issued JWT)

A pure **resource server**: it validates bearer tokens it did not issue, using an RSA **public** key. It holds no signing key, so it can verify tokens but never mint one.

- **The app** (`src/main`) is configured with `public-key-location: classpath:app.pub` — the public half only.
- **The "identity provider"** is played by the test tree (`TokenIssuer`), which holds the matching private key `src/test/resources/app.key` and signs RS256 tokens. In production this is a real IdP (Keycloak/Auth0/Okta/Google) and the app instead fetches its public keys from a JWK Set URL.
- **Authorization is by scope**: `GET /api/books` needs `SCOPE_books:read`, `DELETE /api/books/{id}` needs `SCOPE_books:write`.

This runs **fully offline** — no identity provider required.

## Run the tests

```bash
mvn -q test
```

`OAuth2ResourceServerTest` (9 cases): public route open, no-token `401`, valid token `200`, read scope reads but can't delete (`403`), write scope deletes (`200`), and — the ones that matter — an **expired** token and a token **signed by a different key** both rejected with `401`.

## Run it and curl it (offline, no IdP)

```bash
# 1. print two ready-to-use tokens (this plays the role of the IdP's token endpoint)
mvn -q test -Dtest=PrintToken
#    copy the token under "scope: books:read"  -> RO
#    copy the token under "scope: books:read books:write" -> RW

# 2. boot the resource server
mvn -q spring-boot:run
```

```bash
RO=<paste the books:read token>
RW=<paste the books:read books:write token>

curl -i http://localhost:8080/public/hello                                   # 200 (public)
curl -i http://localhost:8080/api/me                                         # 401 (no token)  -> WWW-Authenticate: Bearer
curl -s http://localhost:8080/api/me           -H "Authorization: Bearer $RO"  # 200 {"subject":"alice","scopes":"books:read",...}
curl -i http://localhost:8080/api/books        -H "Authorization: Bearer $RO"  # 200 (has SCOPE_books:read)
curl -i -X DELETE http://localhost:8080/api/books/1 -H "Authorization: Bearer $RO"  # 403 insufficient_scope
curl -i -X DELETE http://localhost:8080/api/books/1 -H "Authorization: Bearer $RW"  # 200 (has SCOPE_books:write)
curl -i http://localhost:8080/api/books        -H 'Authorization: Bearer not.a.jwt' # 401 (malformed)
```

**401 vs 403 here:** `401` = no / malformed / expired / wrongly-signed token (`WWW-Authenticate: Bearer`). `403` = a valid token whose `scope` claim lacks the required scope (`WWW-Authenticate: Bearer error="insufficient_scope"`). Both come from the framework's bearer-token handlers via `setStatus` — unlike topic 08, there is no `sendError`/`/error` re-dispatch to turn the `403` into a `401`.

## Point it at a real identity provider (optional, needs Docker)

Run a local Keycloak:

```bash
docker run --rm -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:25.0 start-dev
```

In the admin console (`http://localhost:8081`, admin/admin): create a realm `demo`, a client with **Direct access grants** enabled, and a user with a password. Then in `application.yml` comment out `public-key-location` and set:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8081/realms/demo
```

Spring will discover the realm's JWK Set and validate tokens (and the `iss` claim). Fetch a real token and use it exactly as above:

```bash
curl -s -X POST http://localhost:8081/realms/demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=<your-client> \
  -d username=<user> -d password=<pass> | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
```

> The offline path above is the auto-verified one (`mvn test` + the local key pair). The Keycloak steps are standard but depend on your realm/client/user setup.

## Notes

- Depends on `spring-boot-starter-oauth2-resource-server` (topic 09's new starter). It brings the Nimbus JOSE code and installs the `BearerTokenAuthenticationFilter` that **replaces** topic 08's hand-written filter.
- `app.pub` (public) ships in `src/main/resources`; `app.key` (private) is a **test** resource only — the running app never has access to a signing key, which is the "no shared secret / can't forge" property made concrete.
- The public/private pair was generated with:
  `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out app.key && openssl rsa -in app.key -pubout -out app.pub`
