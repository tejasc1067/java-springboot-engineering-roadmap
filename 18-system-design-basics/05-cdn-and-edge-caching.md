# CDN and Edge Caching

A **CDN** (content delivery network) is a set of caching servers, called **edges**, spread around the world. Users connect to the nearest edge instead of to your servers (the **origin**). If the edge already has a copy of the response, it answers in a few milliseconds and your app never sees the request. The rules for what an edge may store, for how long, and for whom are plain HTTP headers that your Spring Boot app sets. Get them right and a CDN absorbs most of your read traffic; get them wrong and it serves one user's account page to another.

Module 15 cached data inside and next to the application. This topic is caching *in front of* it, controlled by HTTP.

---

## The problem this solves

Your shop's product pages, images, and JavaScript are the same for every visitor. Without a CDN:

- **Distance costs time.** A user in Sydney talking to a server in Frankfurt pays roughly 300 ms of network round trip per request before your code runs. An edge in Sydney answers in a few milliseconds.
- **Every request reaches the origin.** A product page viewed a million times an hour is built a million times an hour, by servers you pay for (topic 01's back-of-envelope numbers).
- **Traffic spikes land on you.** A sale or a viral link multiplies requests for the same few URLs. A shared cache absorbs exactly that pattern.

The cost is a second copy of your responses, which you control only through headers: staleness, privacy, and invalidation are now HTTP problems.

## How it works

```text
user (Sydney) --> edge (Sydney) --hit--> response from the edge's copy
                       |
                       +--miss--> origin (your Spring Boot app) --> response + Cache-Control
                                                                     edge stores it if allowed
```

The edge is a **shared cache**: one stored response can be given to many users. The browser has its own **private cache**, for one user only. `Cache-Control` tells both what they may do.

### `Cache-Control`, the directives you'll use

| Directive | Meaning |
|---|---|
| `max-age=60` | fresh for 60 seconds; any cache may reuse it without asking the origin |
| `s-maxage=300` | for **shared** caches (CDNs, proxies) only, overrides `max-age` |
| `public` | any cache may store it, even for a request that had an `Authorization` header |
| `private` | only the user's own browser may store it, never a shared cache |
| `no-cache` | may be stored, but must be **revalidated** with the origin before every use |
| `no-store` | must not be stored anywhere |
| `immutable` | this URL's content will never change; don't even revalidate |
| `stale-while-revalidate=30` | for 30 s after expiry, serve the stale copy immediately and refresh in the background |

`no-cache` is the most misread one: it doesn't mean "don't cache". `no-store` does.

In Spring MVC you set it on the response:

```java
@GetMapping("/products/{id}")
ResponseEntity<String> product(@PathVariable long id) {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).sMaxAge(Duration.ofMinutes(5)).cachePublic())
            .body(...);
}
```

which sends `Cache-Control: max-age=60, public, s-maxage=300`: browsers keep it for a minute, the CDN for five (`PublicCachingTest.theAppSaysHowLongTheResponseMayBeCached`).

### The demo's edge

`EdgeCache.java` is a toy CDN edge of about 125 lines, following the main rules of HTTP caching (RFC 9111). The tests put it in front of the real Spring app, called through MockMvc, with a simulated clock. `ShopController.renders` counts how many times the app built a response, which is what a CDN saves you.

From `PublicCachingTest`:

- `aThousandRequests_oneOriginRender`: 1,000 requests for `/products/1`, 1 render, 999 edge hits.
- `theEdgeUsesSMaxage_notTheBrowsersMaxAge`: at 120 s the edge still serves its copy (past `max-age=60`, within `s-maxage=300`); at 320 s it goes back to the origin.
- `aChangeIsInvisibleUntilTheEntryExpires`: change the product, and the edge serves the old version until its 5 minutes are up. That is the price of every TTL (module 15 topic 04), and it decides how long a TTL you can afford.

### Validators: ETag and 304

When a cached copy expires, the cache doesn't have to download it again. It can ask "has it changed?" by sending the copy's **ETag** (a version tag the origin gave it) in `If-None-Match`. If it hasn't changed, the origin answers `304 Not Modified` with no body, and the cache keeps using its copy. `Last-Modified` with `If-Modified-Since` does the same with a timestamp.

Spring offers two ways to produce ETags, and they save different things (`EtagTest`):

- **`ShallowEtagHeaderFilter`** hashes the response body after your controller has built it. A matching `If-None-Match` gets a 304 with an empty body, so the 10 KB report isn't sent again, but the report was still built: `renders` goes to 2 (`shallowEtag_saves10kOfTransfer_butTheReportIsStillBuiltEveryTime`). It saves bandwidth, not CPU or database work.
- **A deep ETag** comes from something cheap you already know, such as a version column. `WebRequest.checkNotModified(etag)` answers 304 *before* any work is done: `renders` stays at 1 (`deepEtag_checkNotModified_answers304_withoutDoingTheWork`).

```java
@GetMapping("/catalog/{id}")
ResponseEntity<String> catalog(@PathVariable long id, WebRequest request) {
    String etag = "\"v" + productVersion + "\"";
    if (request.checkNotModified(etag)) return null;   // Spring has already set 304 and the ETag
    return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(...);
}
```

Combined with `no-cache`, this gives an edge that never serves stale data and still saves most of the work: three requests cause one render and two small 304 revalidations, and a change shows up on the very next request (`noCache_theEdgeStoresIt_butRevalidatesEveryUse`).

### Cache keys and `Vary`

A cache stores responses under a **key**, normally the URL. If the same URL can return different responses depending on a request header, such as `Accept` (JSON or CSV), `Accept-Language`, or `Accept-Encoding`, the response must say so with `Vary: Accept`. The cache then keeps one copy per value of that header.

### Fingerprinted assets: invalidation by changing the URL

Purging a CDN (asking every edge to drop a URL) is slow, sometimes rate-limited, and easy to forget. For static files there's a better way: put a hash of the file's content in its URL. A new version is a new URL, which no cache has seen, so the old one can be cached for a year with no risk.

Spring Boot does this with two settings (`application.properties`):

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
spring.web.resources.cache.cachecontrol.max-age=365d
spring.web.resources.cache.cachecontrol.cache-public=true
```

`ResourceUrlProvider` turns `/js/app.js` into `/js/app-771186b4dcbefc212e9d2c1fc105df76.js`, served with `Cache-Control: max-age=31536000, public` (`FingerprintedAssetsTest.theAssetUrlContainsAHashOfItsContent`). Spring's `CacheControl` has an `immutable()` method for your own responses, but Spring Boot has no property to add `immutable` to static resources.

## Broken: a personalised response the edge can share

```java
@GetMapping("/broken/account")
ResponseEntity<String> accountBroken(@CookieValue("session") String user) {
    return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))   // no public, so it's private... right?
            .body(accountOf(user));
}
```

Not marking a response `public` doesn't make it private. With only `max-age`, a shared cache is allowed to store it. The user is identified by a cookie, and the URL is the same for everyone, so the edge's key is the same for everyone (`PersonalizedResponseTest.broken_aPlainMaxAge_letsTheEdgeServeAnnsAccountToBob`):

```text
Ann  GET /broken/account  -> edge miss -> origin builds Ann's page -> edge stores it
Bob  GET /broken/account  -> edge hit  -> Bob receives Ann's name and address
```

The app never even sees Bob's request: `renders` is 1. This is a real and repeated class of incident: a cache configuration change, and users see each other's accounts.

HTTP has one safety net, and it doesn't cover cookies: a shared cache must not reuse a response to a request carrying an `Authorization` header unless the response says `public` or `s-maxage`. With a bearer token, the same endpoint isn't shared (`theAuthorizationRule_protectsTokenRequests_butNotCookies`). With a session cookie, nothing protects you.

## Fixed: say `no-store` (or `private`)

```java
return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accountOf(user));
```

Each user gets their own response and the edge keeps nothing (`fixed_noStore_everyUserGetsTheirOwnResponse_andTheEdgeKeepsNothing`). Use `no-store` for anything sensitive. Use `private, max-age=...` when the browser may keep it but no shared cache may. And set the headers on purpose for every endpoint behind a CDN, instead of relying on whatever the CDN does by default.

## Broken: the same URL in two formats, without `Vary`

`/broken/prices/7` returns JSON or CSV depending on `Accept`. Spring doesn't add `Vary: Accept` for you (`VaryTest.springDoesNotAddVaryForYou`). The first client asks for JSON and the edge stores it under the URL; the next client asks for CSV and gets JSON (`broken_withoutVary_theCsvClientGetsTheJsonThatWasCachedFirst`).

Fixed with `ResponseEntity.ok().varyBy("Accept")`: the edge keeps one entry per format, two renders and then hits for both (`fixed_varyAccept_keepsOneEntryPerFormat`). Keep `Vary` to headers with few values: `Vary: Cookie` or `Vary: User-Agent` gives one copy per user or per browser version, which means almost no hits.

## What to put on a CDN, and what not to

| Put it on the CDN | Keep it off (or `no-store`/`private`) |
|---|---|
| fingerprinted JS, CSS, fonts: cache for a year | anything for one logged-in user: account, cart, orders |
| images and downloads | responses that depend on a cookie or session |
| public API responses that are the same for everyone (catalogue, prices, articles), with a short `s-maxage` | write requests (`POST`, `PUT`, `DELETE`), which caches don't store |
| `no-cache` + ETag responses that change unpredictably but are read often | data that must be correct at the moment of a decision (stock at checkout, balances) |

## When to use it / when not to

- **Use a CDN** for any public website or API with users far from your servers, for static assets always, and for read-heavy public data that tolerates seconds or minutes of staleness.
- **Short TTL plus revalidation** (`s-maxage=30` or `no-cache` with a deep ETag) is often the best of both: most requests are hits or cheap 304s, and changes appear quickly.
- **Don't expect a CDN to help** with personalised pages or write-heavy APIs. Those need application-level caching (module 15) and enough origin capacity (topic 01).
- Managed CDNs have their own defaults and override settings. Check what yours actually does with your headers, ideally with a test like `PersonalizedResponseTest` against a staging URL.

## Common pitfalls

- **A plain `max-age` on personalised data.** A shared cache may store it. Use `no-store` or `private`.
- **`no-cache` used to mean "don't cache".** It means "revalidate every time". Use `no-store` to forbid storing.
- **Content negotiation without `Vary`.** One format is served to every client. Add `varyBy(...)`, and only for low-variety headers.
- **A long `max-age` on an unversioned URL.** `spring.web.resources.cache.*` applies to every static file, so `/js/app.js` is cached for a year too (`FingerprintedAssetsTest.theYearLongHeaderAlsoGoesOnTheUnversionedUrl`). Always link the fingerprinted URL from your pages.
- **Expecting `ShallowEtagHeaderFilter` to reduce server load.** It still builds every response. Use a deep ETag when the work is the cost.

---

## Code examples

`CODE_EXAMPLES/05-cdn-and-edge-caching/cdn-demo/`. Spring Boot 3.5 with MockMvc, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/cdn/ShopController.java`: the endpoints and their `Cache-Control`, `Vary`, and ETag headers.
2. `src/main/java/com/example/cdn/EdgeCache.java`: the toy edge: freshness, `s-maxage`, `private`/`no-store`, the `Authorization` rule, ETag revalidation, `Vary`.
3. `src/test/java/com/example/cdn/EdgeTestBase.java`: the edge in front of the app through MockMvc.
4. `src/test/java/com/example/cdn/PublicCachingTest.java`: hits, `s-maxage`, staleness.
5. `src/test/java/com/example/cdn/PersonalizedResponseTest.java`: Ann's account served to Bob, and the fix.
6. `src/test/java/com/example/cdn/VaryTest.java`: the wrong format, and `Vary: Accept`.
7. `src/test/java/com/example/cdn/EtagTest.java`: shallow vs deep ETags, `no-cache` revalidation.
8. `src/main/resources/application.properties` and `src/test/java/com/example/cdn/FingerprintedAssetsTest.java`: content-hashed asset URLs.

---

## Try this yourself

1. Change `/broken/account` to `CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate()` and run `PersonalizedResponseTest`. The broken test now fails, because Bob gets his own account. Why does `private` stop the edge, and what may Ann's own browser still do with the response?
2. Edit `src/main/resources/static/js/app.js` and run `FingerprintedAssetsTest` again, printing the versioned URL. Why does a changed file never need a CDN purge?

## Self-check

1. What's the difference between `no-cache`, `no-store`, and `private`? Which would you put on a user's order history, and on a product image?
2. A response has `Cache-Control: max-age=60` and depends on a session cookie. Walk through how the second user ends up with the first user's data, and why the same endpoint called with a bearer token wouldn't leak.
3. `ShallowEtagHeaderFilter` makes repeat requests return 304. What does it save, what doesn't it save, and how does `checkNotModified` with a version-based ETag differ?
