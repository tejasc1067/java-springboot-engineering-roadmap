# 06 — Form login vs HTTP Basic vs stateless

One app, two filter chains showing the two ends of the session axis:

- **`/stateful/**`** — **form login**: log in once, get a `JSESSIONID` cookie, ride it on later requests.
- **`/stateless/**`** — **HTTP Basic + `SessionCreationPolicy.STATELESS`**: send credentials every request, no session ever created.

User: `alice` / `password`.

## Run it

```bash
mvn -q spring-boot:run
```

**Stateless side (HTTP Basic) — try it with `curl`:**

```bash
curl -i http://localhost:8080/stateless/me
# HTTP/1.1 401                                  <- anonymous, clean challenge (no redirect)

curl -i -u alice:password http://localhost:8080/stateless/me
# HTTP/1.1 200   {"user":"alice"}               <- credentials on THIS request; no Set-Cookie issued
```

Note there is **no `Set-Cookie: JSESSIONID`** on the response — nothing is remembered. Every call must carry credentials.

**Stateful side (form login) — best seen in a browser:**

1. Open `http://localhost:8080/stateful/me`. You're **redirected to a login form** (not given a 401).
2. Log in as `alice` / `password`. You're redirected back, and the browser now holds a `JSESSIONID` cookie.
3. Refresh `/stateful/me`. It returns `{"user":"alice"}` — authenticated by the session cookie, with no credentials re-sent. Delete the cookie (or open a private window) and you're back to the login page.

## Run the tests

```bash
mvn -q test
```

`SessionVsStatelessTest` proves it: form login creates a reusable session (and anonymous → redirect to `/login`); Basic authenticates per request (anonymous → 401) and leaves **no** session behind.
