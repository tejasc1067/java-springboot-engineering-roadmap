# url-shortener-demo

The core of the URL shortener from topic 09, in plain Java, with each design trade-off asserted in a test.

- `EstimatesTest` — traffic, storage, cache size, code length, and the birthday bound for truncated hashes.
- `HashCodeGeneratorTest` — two URLs sharing a 3-character code after 208 URLs (`/item/190` and `/item/207` → `jUw`), and why retrying a hash can't fix it.
- `SequenceCodeGeneratorTest` — guessing someone else's link from your own; two instances with their own ranges never colliding.
- `RandomCodeGeneratorTest` — a forced collision refused by the store and retried with a new code.
- `ShortenerServiceTest` — duplicate links from a retried create, and the idempotency key that prevents them; custom aliases; cache-aside (1,000 redirects, 1 store read); 404s reaching the store every time; 301 vs 302 seen from a browser.

Run (no Docker, no Spring):

```
mvn -q test
```

`HashCodeGeneratorTest` prints the colliding pair it found.
