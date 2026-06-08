# API versioning — URI, header, and media-type

You ship a service, clients build against it, and a year later you need to change a response shape in a way that would break the old clients. You can't break the old clients. The answer is **versioning** — running the old contract alongside the new one until clients migrate. This topic covers the three places people put the version, and which one to pick when.

---

## The problem this solves

Most changes to an API are **additive** and don't need a new version: adding a new endpoint, adding a new optional field, adding a new query parameter. Old clients keep working because nothing they relied on changed.

Some changes are **breaking** and do need a new version:

- Removing a field from a response.
- Renaming a field (`fullName` -> `name`).
- Changing the type of a field (`age` from int to a structured `{years, months}` object).
- Removing an endpoint.
- Changing the meaning of a status code.

The temptation when you hit one of these is to "just send a note to all the clients." That works at three clients. It fails at thirty, and it fails completely once one of the clients is a mobile app you don't control.

The discipline: **breaking changes get a new version. The old version stays live until you have an explicit plan to retire it.**

---

## The three approaches

### 1. URI versioning — `/api/v1/books`

The version is part of the URL.

```text
GET /api/v1/books         old client
GET /api/v2/books         new client (response shape changed)
```

In Spring:

```java
@RestController @RequestMapping("/api/v1/books") class BookControllerV1 { ... }
@RestController @RequestMapping("/api/v2/books") class BookControllerV2 { ... }
```

Each version is a separate `@RestController`. They share service code where the logic is the same and diverge where the contract changed.

**Pros**

- **Visible in every URL.** You see what version is in play just by looking at a log line or a `curl` command.
- **Easy to test.** `curl /api/v1/books` and `curl /api/v2/books` work side-by-side.
- **Easy to cache.** CDNs and reverse proxies key on URL; different URLs cache independently.
- **Easy to route.** A gateway can route `/api/v1/*` to one service version and `/api/v2/*` to another.

**Cons**

- "Not pure REST." Purists argue the URL should identify a resource, not a version of an API. The resource `book 42` is the same thing in v1 and v2; the URI shouldn't change. Real-world weight of this argument: low.
- Two URLs for the same conceptual thing. Doc duplication.

**Verdict:** the **default choice** for most teams. Most large public APIs (GitHub, Stripe before 2014, Twitter, Twilio) use it.

### 2. Header versioning — `X-API-Version: 1`

The version is a custom request header.

```http
GET /api/books
X-API-Version: 1
```

In Spring:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping(headers = "X-API-Version=1")
    public List<BookV1> listV1() { ... }

    @GetMapping(headers = "X-API-Version=2")
    public List<BookV2> listV2() { ... }
}
```

Spring routes on the header value. Two methods can share the same URL.

**Pros**

- **One URL across versions.** Conceptually consistent with REST: the resource URL is stable.
- **Easy to default.** If the header is missing, you can choose the current version (or reject — pick one).

**Cons**

- **Invisible in URLs.** Logs, browser bookmarks, `curl` commands all need the header. Debugging is harder.
- **Cacheable only with `Vary: X-API-Version`.** Forget it and caches conflate the two versions.
- **Custom headers per consumer.** Standard `Accept` would be cleaner (see media-type below); custom headers fragment the ecosystem.

**Verdict:** rarely the best choice. If you want "one URL, multiple versions," use media-type versioning instead.

### 3. Media-type versioning — `Accept: application/vnd.example.v1+json`

The version is in the `Accept` header, encoded into a custom media type.

```http
GET /api/books
Accept: application/vnd.example.v1+json
```

In Spring:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping(produces = "application/vnd.example.v1+json")
    public List<BookV1> listV1() { ... }

    @GetMapping(produces = "application/vnd.example.v2+json")
    public List<BookV2> listV2() { ... }
}
```

**Pros**

- **REST-pure.** The URL identifies the resource; the representation (including version) is negotiated via `Accept`. This is the Fielding-blessed approach.
- **One URL.** Same as header versioning.
- **Standard mechanism.** `Accept` is HTTP, not a custom header — every client library understands it.

**Cons**

- **Awkward to test by hand.** You always need to set the `Accept` header. `curl` becomes verbose.
- **Cacheable only with `Vary: Accept`.** Same caveat.
- **Less visible.** Same downside as header versioning.
- **Vendor MIME types** (the `vnd.example.v1+json` part) are nonstandard and ugly.

**Verdict:** correct in theory; rare in practice outside a few high-profile APIs (GitHub uses it for some endpoints; the Mendeley API uses it).

---

## The pragmatic recommendation

For most teams building most services, **URI versioning is the right default.**

- It optimizes for what people actually do: debug from logs, share URLs, test with `curl`, route at the gateway.
- The theoretical cleanliness of media-type versioning rarely pays back the operational cost.
- Header versioning gets the worst of both worlds.

The exception: **public APIs with a long-lived client SDK** (think Stripe). They often go media-type. They have the staff and the docs to make it work, and the SDK hides the awkwardness from end-users.

Avoid two patterns that look like versioning but aren't:

- **`?v=2` query parameter.** Same drawbacks as URI versioning with extra ugliness. Used in some old APIs; not recommended for new ones.
- **No versioning, "we'll handle breaks with feature flags."** Lasts six months before someone forgets, ships a breaking change, and the support burden eats the team. Plan for versioning from day one.

---

## What "v1" actually contains

A version is not just the response shape. It is a **contract**: every endpoint, every request body, every response body, every status code, every error format. When you mint v2:

- Pin the v1 contract — write tests against it that exercise the actual JSON, the actual codes. Module 09's MockMvc tests are this in microcosm.
- Generate the v1 OpenAPI spec (topic 13) and freeze it.
- New code that needs to break the contract goes in v2; v1 keeps using the old shape forever (or until you publicly deprecate it).

Without the contract being captured anywhere, "v1" is just two letters in a URL.

---

## Common pitfalls

- **No version at all on day one.** "We'll add it later." You won't — once clients are live, retroactively adding `/api/v1` to every URL is a flag day. Start with `/api/v1` even if there is no v2 in sight.
- **Versioning per-endpoint.** `/api/v1/books` and `/api/v3/orders` in the same service. Confusing. Version the whole API together.
- **Skipping version numbers.** Going `v1 -> v3` confuses everyone. Bump by one.
- **Maintaining infinite versions.** "v1, v2, v3, v4 all live." Set a deprecation policy: announce, give clients a window (six months / a year), retire. The benefit of versioning is the ability to *retire* old contracts safely; never retiring negates it.
- **Confusing "feature flags" with "versions."** Feature flags toggle behaviour; versions freeze contracts. They solve different problems.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/14-api-versioning/versioning-demo`. It exposes three controllers, one per strategy, all serving the same conceptual `Book` resource. v1 returns `{id, name}`; v2 returns `{id, title, author}` (a breaking rename + new field).

1. Run `mvn -q spring-boot:run`. Then:

   URI versioning:
   ```
   curl -s http://localhost:8080/api/v1/books
   curl -s http://localhost:8080/api/v2/books
   ```

   Header versioning:
   ```
   curl -s -H "X-API-Version: 1" http://localhost:8080/api/header-books
   curl -s -H "X-API-Version: 2" http://localhost:8080/api/header-books
   ```

   Media-type versioning:
   ```
   curl -s -H "Accept: application/vnd.example.v1+json" http://localhost:8080/api/media-books
   curl -s -H "Accept: application/vnd.example.v2+json" http://localhost:8080/api/media-books
   ```

2. Compare the v1 and v2 shapes — `name` vs `{title, author}`. This is the kind of breaking change versioning exists to handle.

3. Remove the `X-API-Version` header from the header-versioning call. Note Spring returns 404 (or 406) — no handler matched. Decide what your service should do for a missing header: route to the latest, route to v1, reject? Each is a real choice.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`BookV1.java`** / **`BookV2.java`** — the two contracts.
3. **`UriVersionedController.java`** — `@RequestMapping("/api/v1/...")` and `@RequestMapping("/api/v2/...")`.
4. **`HeaderVersionedController.java`** — `@GetMapping(headers = "X-API-Version=1")`.
5. **`MediaTypeVersionedController.java`** — `@GetMapping(produces = "application/vnd.example.v1+json")`.
6. **`VersioningTest.java`** — MockMvc proves all three strategies route correctly.

## Self-check

1. List the three places you can put a version. Which is the standard default for new services and why?
2. A client calls a header-versioned endpoint without the `X-API-Version` header. What are three reasonable behaviors, and which would you pick?
3. Why is "we'll handle breaking changes with feature flags" not the same as versioning?
