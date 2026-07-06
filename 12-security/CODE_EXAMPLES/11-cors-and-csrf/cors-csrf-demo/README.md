# 11 — CORS and CSRF

A **cookie/session** app (form login → `JSESSIONID`), so CSRF protection is **on** (the opposite of topics 08–09's token APIs, where disabling it was correct). CORS is configured to allow one browser origin, `http://localhost:3000`.

- `GET /` — public.
- `GET /api/whoami` — authenticated, CSRF-exempt (reads don't change state).
- `POST /api/transfer` — authenticated, CSRF-protected (the classic forgery target).

## Run the tests

```bash
mvn -q test
```

`CorsAndCsrfTest` (6 cases): a `GET` needs no token; a `POST` without a CSRF token is `403`; a `POST` with a token is `200`; a CORS preflight from the allowed origin is approved; one from a disallowed origin is rejected with no allow-origin header; a real cross-origin request carries the allow-origin header.

## See it on the wire

```bash
mvn -q spring-boot:run
```

### CSRF — a state-changing request needs a token

```bash
# No CSRF token -> 403, before auth is even considered. A forged cross-site POST lands exactly here:
# it can ride the victim's session cookie but cannot supply the token.
curl -i -X POST http://localhost:8080/api/transfer          # 403
curl -i -X POST http://localhost:8080/login -d "username=alice&password=password"   # 403 (login is state-changing too)
```

The legitimate SPA reads the `XSRF-TOKEN` cookie that Spring sets and echoes it in the `X-XSRF-TOKEN` header; the success path (`200` with a token) is asserted in `CorsAndCsrfTest#postWithCsrfTokenSucceeds`.

### CORS — which browser origins may read this API

```bash
# Allowed origin: preflight approved, browser will let the SPA read the response
curl -i -X OPTIONS http://localhost:8080/api/transfer \
  -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST"
#   HTTP/1.1 200
#   Access-Control-Allow-Origin: http://localhost:3000
#   Access-Control-Allow-Methods: GET,POST
#   Access-Control-Allow-Credentials: true

# Disallowed origin: refused, no Access-Control-Allow-Origin -> the browser blocks the read
curl -i -X OPTIONS http://localhost:8080/api/transfer \
  -H "Origin: http://evil.example" -H "Access-Control-Request-Method: POST"
#   HTTP/1.1 403
#   Invalid CORS request
```

> Note the `curl` calls to `/api/transfer` above still *reach* the server — CORS never blocks a non-browser client. CORS only controls whether browser JavaScript on another origin may **read** the response. It is not access control.

## Prove the vulnerability

Add `.csrf(csrf -> csrf.disable())` to `SecurityConfig` and re-run `mvn test`: `postWithoutCsrfTokenIsForbidden` now **fails** — the tokenless POST succeeds, which is precisely the forgery a malicious page would exploit against a logged-in user. That failing test is the bug. Restore the line.

## Notes

- CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()` so a SPA's JavaScript can read the `XSRF-TOKEN` cookie and send it back as `X-XSRF-TOKEN`.
- `allowCredentials(true)` requires listing exact origins — the CORS spec forbids pairing credentials with `"*"`, and Spring rejects that combination at runtime.
- Compare `SecurityConfig` here to topics 08–09: those disabled CSRF because a bearer token in the `Authorization` header can't be attached by a cross-site page. This app's session cookie *is* auto-attached, so CSRF protection is required.
