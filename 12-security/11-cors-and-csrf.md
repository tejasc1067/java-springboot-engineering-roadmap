# CORS and CSRF — two cross-site mechanisms people constantly confuse

These are the two browser-security features that generate the most copy-pasted, least-understood config in Spring apps: `.cors()` and `.csrf().disable()`. They sound similar ("cross-site something") and are completely different. **CORS** decides which other web origins may *read* your API's responses; disabling it wrongly just breaks your frontend. **CSRF** stops a malicious site from making your logged-in users *perform actions*; disabling it wrongly opens a real vulnerability. This topic pins down what each is, and — the payoff of the whole module — exactly when `.csrf().disable()` is correct (a token API, topics 08–09) and when it's a serious bug (a cookie app, topic 10).

---

## The problem each one solves

**CORS** exists because of the browser's **Same-Origin Policy**: JavaScript running on `https://app.com` is not allowed to *read* responses from `https://api.com` (a different origin). That policy protects users, but it also blocks your own legitimate SPA-on-3000 talking to your API-on-8080. CORS is the server's way to say "these specific other origins *are* allowed to read me."

**CSRF** exists because browsers attach credentials — cookies especially — **automatically** to every request to a domain. If you're logged into `bank.com` and visit `evil.com`, a form on `evil.com` can POST to `bank.com/transfer` and the browser will *helpfully attach your bank.com session cookie*. The action runs as you. CSRF protection is how the server distinguishes a request its own pages made from one a foreign page forged.

---

## How it works

### CORS is enforced by the browser, not your server

Your server just emits `Access-Control-Allow-*` headers; the **browser** decides whether to hand the response to the calling JavaScript. For "non-simple" requests (a JSON `Content-Type`, a custom header, or methods like `PUT`/`DELETE`), the browser first sends a **preflight** `OPTIONS` request asking "may I?", and only proceeds if the answer allows it:

```
# allowed origin -> browser will let the SPA read the response
OPTIONS /api/transfer   Origin: http://localhost:3000   Access-Control-Request-Method: POST
  -> 200  Access-Control-Allow-Origin: http://localhost:3000
          Access-Control-Allow-Methods: GET,POST
          Access-Control-Allow-Credentials: true

# unknown origin -> no allow-origin header, browser blocks the read
OPTIONS /api/transfer   Origin: http://evil.example   ...
  -> 403  Invalid CORS request        (no Access-Control-Allow-Origin)
```

The crucial mental model: **CORS is not access control for your API.** It does not stop anyone from *calling* your endpoint — `curl`, Postman, and other servers ignore CORS completely and will happily hit it. CORS only governs whether *browser JavaScript on another origin* may read the response. It protects your users, not your data.

```java
config.setAllowedOrigins(List.of("http://localhost:3000"));   // explicit origins, never "*" with credentials
config.setAllowedMethods(List.of("GET", "POST"));
config.setAllowCredentials(true);                             // allow the session cookie cross-origin
```

### CSRF works precisely because the attacker never needs to read the response

The forged request is *fire-and-forget*. The attacker doesn't care what comes back — the damage (the transfer, the password change) is done by the request itself. A page on `evil.example` needs nothing but this:

```html
<!-- on evil.example: auto-submits to your app using the victim's ambient cookie -->
<form action="https://your-app.com/api/transfer" method="POST" id="f">…</form>
<script>document.getElementById('f').submit()</script>
```

Because the attacker never reads the response, **CORS does not defend against CSRF** — CORS is about reading, CSRF is about doing. The defense is a **CSRF token**: an unpredictable value the server issues, which your own pages include on state-changing requests but a foreign page *cannot obtain* (the Same-Origin Policy stops `evil.example` from reading it). No token → rejected:

```
POST /api/transfer   (session cookie present, but no CSRF token)   -> 403
POST /api/transfer   (session cookie + valid X-XSRF-TOKEN)          -> 200
GET  /api/whoami     (no token needed)                             -> 200
```

CSRF protection applies only to **unsafe methods** (`POST`/`PUT`/`DELETE`/`PATCH`). `GET`/`HEAD`/`OPTIONS` are exempt because they must not change state — there's nothing to forge. Spring's `CookieCsrfTokenRepository.withHttpOnlyFalse()` puts the token in an `XSRF-TOKEN` cookie the SPA's JS reads and echoes in the `X-XSRF-TOKEN` header — the standard SPA pattern.

### The decision: when is `.csrf().disable()` correct?

This is the question the whole module has been building toward. CSRF is only a threat when the browser attaches credentials **automatically**. So it comes down to *how you authenticate*:

| How the request proves who you are | Sent automatically by the browser? | CSRF protection needed? | Example |
|---|---|---|---|
| Session cookie (`JSESSIONID`) | **Yes** | **Yes — keep it on** | topic 10, topic 06 form login |
| HTTP Basic (browser caches it) | **Yes** | **Yes** (for browser clients) | — |
| JWT/token in a **cookie** | **Yes** (it's a cookie) | **Yes** | — |
| Bearer token in the `Authorization` header (set by JS) | **No** | **No — disabling is correct** | topics 08–09 |

A cross-site page **cannot** set an `Authorization: Bearer` header on your behalf and **cannot** read your token, so a pure bearer-token API has no CSRF vector — which is exactly why topics 08 and 09 disabled CSRF, and it was correct, not lazy. The trap in the last-but-one row: "we use JWTs, so we don't need CSRF" is **wrong if the JWT lives in a cookie**. It's the *transport* (an auto-sent cookie) that creates the risk, not the token format.

---

## Vulnerable / broken version

Take this topic's demo — a cookie/session app — and add `.csrf(csrf -> csrf.disable())`. Now the `evil.example` form above works: the victim's browser attaches the `JSESSIONID`, `POST /api/transfer` runs, and money moves, all without the victim clicking anything on your site. The demo proves the fix: with CSRF on, that forged POST arrives without a token and gets **403**. Removing that one line is the difference between safe and exploitable — which is why blindly copying `.csrf().disable()` into a session app is one of the most common real security bugs.

The mirror-image CORS mistake:

```java
config.setAllowedOrigins(List.of("*"));      // any origin
config.setAllowCredentials(true);            // ...with the user's cookies
```

This says "any website may make credentialed requests and read the responses using this user's session." The spec forbids literal `*` with credentials (and Spring rejects the combination), so people "fix" it by *reflecting the request's `Origin` back* — which is the same hole wearing a disguise: now every origin is allowed. Allow a known list of origins, and only enable credentials for those you trust.

---

## When to use it / when not

- **Configure CORS** when a browser app on a *different origin* must call your API (SPA on `:3000` → API on `:8080`, or a separate frontend domain). List the exact origins. Same-origin apps (page and API on one origin) need no CORS at all.
- **Never treat CORS as protection.** It won't stop non-browser callers; it's not authorization. If you're reaching for CORS to "secure" an endpoint, you want authentication/authorization (topics 04–09) instead.
- **Keep CSRF on** for any app authenticated by something the browser sends automatically — sessions, Basic, or a cookie-stored token. This is the default; leave it.
- **Disable CSRF** only for a stateless API authenticated by a bearer token in a header (topics 08–09). There, it's correct. Don't disable it "to make the 403 go away" on a session app — the 403 is the protection working.

---

## Common pitfalls

- **`.csrf().disable()` on a cookie/session app.** The headline bug. If the browser auto-sends your credential, you need CSRF protection. Removing it makes every state-changing endpoint forgeable.
- **"We use JWT, so no CSRF."** Only true if the token is in the `Authorization` header. A JWT in a cookie is auto-sent → still CSRF-able.
- **Treating CORS as a firewall.** `curl`/servers ignore CORS entirely; it constrains browser JS only. It is not access control.
- **`allowedOrigins("*")` with `allowCredentials(true)`** (or reflecting the `Origin`). This lets any site make credentialed cross-origin reads. Allow a specific list.
- **Conflating the two.** A "CORS error" in the console is never fixed by touching CSRF, and a CSRF 403 is never fixed by touching CORS. Different mechanisms; they only share the word "cross-site."
- **SPA can't read the CSRF token.** If the token cookie is `HttpOnly`, JS can't echo it. Use `CookieCsrfTokenRepository.withHttpOnlyFalse()` (or the header pattern) so the SPA can send it back.
- **Preflight blocked by auth rules.** The `OPTIONS` preflight is unauthenticated; `http.cors(...)` handles it before authorization. If you hand-roll filter ordering and the preflight hits your auth first, you'll get a spurious 401 and a confusing browser error.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/11-cors-and-csrf/cors-csrf-demo` — a cookie/session app (form login) with CSRF on and CORS allowing `http://localhost:3000`.

1. **Run the tests** (`mvn test`) — 6 cases: reads need no token, a tokenless POST is `403`, a POST with a token is `200`, and CORS approves the allowed origin while rejecting `evil.example`.
2. **See it on the wire.** Boot (`mvn spring-boot:run`) and run the `curl` commands in the demo README: a tokenless `POST` → `403`, an allowed preflight → the `Access-Control-Allow-Origin` header, a disallowed one → `403 Invalid CORS request`.
3. **Open the hole.** Add `.csrf(csrf -> csrf.disable())` to `SecurityConfig` and re-run the tests: `postWithoutCsrfTokenIsForbidden` now *fails* because the forgeable POST succeeds. That failing test *is* the vulnerability. Put the line back.
4. **Break CORS two ways.** Change `allowedOrigins` to `"http://localhost:3000"`'s evil twin and watch the preflight get refused; then try `"*"` with `allowCredentials(true)` and see Spring reject the illegal combination at runtime.
5. **Reason about the sibling demos.** Re-read topic 08/09's `SecurityConfig`: they disabled CSRF. Explain out loud why that was *correct* there and would be *wrong* here.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`SecurityConfig.java`** — CSRF left **on** with a cookie token repository, CORS restricted to one origin, form-login session. The comments contrast this with topics 08–09.
3. **`DemoController.java`** — a CSRF-exempt `GET` and a CSRF-protected `POST` (the forgery target).
4. **`CorsAndCsrfTest.java`** — both mechanisms, both directions: token/no-token POST, allowed/disallowed CORS origin.

## Self-check

1. Who actually enforces CORS — your server or the browser? Does configuring CORS stop a `curl` or another server from calling your API?
2. A CSRF attack can't read your API's response. Why is it still dangerous, and which browser behavior makes it possible?
3. Your API authenticates with a bearer token in the `Authorization` header. Is `.csrf().disable()` correct? Now the token is stored in a cookie instead — does your answer change, and why?
4. What's the dangerous CORS misconfiguration involving credentials, and why is "just reflect the Origin header" not a fix?
5. Under CSRF protection, why is a `GET` exempt but a `POST` is not?
