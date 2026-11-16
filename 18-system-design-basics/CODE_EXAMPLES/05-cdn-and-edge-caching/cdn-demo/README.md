# cdn-demo

HTTP caching headers set by a Spring Boot app, tested through `EdgeCache`, a toy CDN edge that follows the main HTTP caching rules (RFC 9111) with a simulated clock. No Docker.

- `PublicCachingTest`: `max-age=60, public, s-maxage=300`; 1,000 requests, 1 render; the edge uses `s-maxage`; a change is invisible until the entry expires.
- `PersonalizedResponseTest`: a plain `max-age` on a cookie-identified account page lets the edge serve Ann's data to Bob; `no-store` fixes it; the `Authorization` rule protects bearer-token requests but not cookies.
- `VaryTest`: Spring doesn't add `Vary`; without it a CSV client gets cached JSON; `varyBy("Accept")` keeps one entry per format.
- `EtagTest`: `ShallowEtagHeaderFilter` returns 304 but still builds the body; a version-based ETag with `checkNotModified` skips the work; `no-cache` revalidates every use.
- `FingerprintedAssetsTest`: `/js/app.js` becomes `/js/app-<md5>.js` with a one-year `Cache-Control`, which the unversioned URL gets too.

Run:

```
mvn -q test
```
