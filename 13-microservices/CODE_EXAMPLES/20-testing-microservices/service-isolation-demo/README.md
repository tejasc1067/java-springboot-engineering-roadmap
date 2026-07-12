# service-isolation-demo

Test `order-service` with `inventory-service` **never started**. WireMock is a real HTTP server
programmed with canned responses, so the *real* `InventoryClient` runs against it — the URL, the
JSON parsing, and the status handling are all actually exercised.

## Stub the downstream — `OrderServiceWireMockTest`

Five behaviours of `OrderService`, each decided by what the stubbed inventory returns:

- **enough stock → CONFIRMED** — and `verify(...)` proves order-service called the right
  endpoint exactly once (something a mock of the client object cannot check).
- **too few units → REJECTED_INSUFFICIENT_STOCK**.
- **404 → REJECTED_UNKNOWN_SKU** — a definite business answer.
- **500 → InventoryUnavailableException** — a 5xx is not a business answer; order-service
  raises a controlled error (→ 503 to its caller) instead of leaking the raw HTTP exception.
- **slow response → InventoryUnavailableException** — `withFixedDelay(1500)` + a 300 ms read
  timeout makes the timeout path (topic 09) testable deterministically.

## Mock the object vs stub the wire — `MockVsStubTest`

Why the two aren't interchangeable:

- **mocking the object validates the decision logic fast** — but the URL, JSON, and 5xx
  handling never run.
- **mocking lets a wrong assumption pass** — we pretend a failed call throws a plain
  `RuntimeException`; it sails through green, and `OrderService`'s `catch (RestClientException)`
  never fires because a bare `RuntimeException` isn't one.
- **stubbing the wire reveals the real handling** — a real 500 makes the real client throw
  `HttpServerErrorException` (a `RestClientException`), which `OrderService` actually
  translates. Only the stub exercised this.

## Run

```bash
mvn -q test        # Tests run: 8, green
```

(The "Mockito is self-attaching" / dynamic-agent lines are JDK warnings, not failures.)

## What a stub does NOT catch

The stub encodes *your* belief about inventory-service's contract. If inventory-service renames
`available` or changes a status code and you don't update the stub, every test here stays green
while production breaks. Closing that gap is **contract testing** — topic 21.
