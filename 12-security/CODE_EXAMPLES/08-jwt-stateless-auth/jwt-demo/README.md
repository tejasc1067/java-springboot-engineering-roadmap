# 08 — JWT stateless auth (self-signed, custom filter)

A stateless, bearer-token API. No sessions. You log in once to get a signed JWT, then send it on every request. Two users — `alice` (`ROLE_USER`) and `admin` (`ROLE_ADMIN`), password `password`.

- **Issue**: `POST /auth/login` checks the password once (the topic 04–05 model) and returns a signed HS256 token.
- **Validate**: a hand-written `JwtAuthFilter` reads `Authorization: Bearer …`, verifies the signature + `exp`, and populates the `SecurityContext`. Topic 09 replaces this filter with `oauth2ResourceServer`.
- **Authorize**: the `roles` claim inside the token drives the same URL rules as topic 07 — now with no server-side lookup.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# 1. public route — no token needed
curl -i http://localhost:8080/public/hello                       # 200

# 2. protected route with no token
curl -i http://localhost:8080/api/me                             # 401

# 3. log in to get a token (store it)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 4. use the token — the claims come back as the authenticated identity
curl -s http://localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
#   -> {"authorities":["ROLE_USER"],"user":"alice"}

# 5. alice's token on an admin route: authenticated, wrong role
curl -i http://localhost:8080/api/admin/stats -H "Authorization: Bearer $TOKEN"   # 403

# 6. admin's token reaches the admin route
ATOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -i http://localhost:8080/api/admin/stats -H "Authorization: Bearer $ATOKEN"  # 200

# 7. wrong password never yields a token
curl -i -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"nope"}'                    # 401
```

**401 vs 403:** `401` = missing / tampered / expired token (we can't authenticate you). `403` = valid token, but the `roles` claim lacks the required role.

## See what the signature does and does not protect

```bash
# The payload is readable by ANYONE — no secret required. (Integrity, not confidentiality.)
echo "$TOKEN" | python3 -c 'import sys,base64,json; p=sys.stdin.read().strip().split(".")[1]; p+="="*(-len(p)%4); print(json.loads(base64.urlsafe_b64decode(p)))'
#   -> {'iss': 'jwt-demo', 'sub': 'alice', 'exp': ..., 'iat': ..., 'roles': 'ROLE_USER'}

# Tamper with one character of the payload and re-send: the signature no longer matches -> 401
BAD="${TOKEN%.*}X.${TOKEN##*.}"   # crude edit; any change breaks the signature
curl -i http://localhost:8080/api/me -H "Authorization: Bearer $BAD"   # 401
```

## Run the tests

```bash
mvn -q test
```

`JwtStatelessTest` (9 cases) covers the full loop: login → use token, the `401`/`403` split, plus the two that matter most — a **tampered** token and an **expired** token both rejected with `401`.

## Configuration

The HS256 signing secret is read from the `DEMO_JWT_SECRET` environment variable, with a local-only fallback in `application.yml`. In production it comes from the environment or a secret manager and is never committed — anyone holding it can forge a token for any user. HS256 requires a secret of at least 32 bytes.

## Notes

- Depends on `spring-security-oauth2-jose` only (for `NimbusJwtEncoder`/`NimbusJwtDecoder`) — **not** the full resource-server starter, because this topic writes its own filter. Topic 09 adds that starter and deletes the filter.
- `JwtAuthFilter` is deliberately *not* a `@Component`: a `Filter` bean gets auto-registered with the servlet container and would run a second time outside the security chain. It's constructed in `SecurityConfig` instead.
- The `403` uses a custom `accessDeniedHandler` that calls `setStatus` rather than the default `sendError` — `sendError` triggers a `/error` re-dispatch that would turn the `403` into a `401`. Topic 12 rebuilds both handlers around `ProblemDetail`.
