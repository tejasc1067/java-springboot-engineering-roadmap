# Testing Microservices

In a monolith you test a piece of behaviour by calling a method: the collaborators are in the same process, so a normal unit or `@SpringBootTest` covers them. Once `order-service` must call `inventory-service` over HTTP to do its job, that stops working — you can't run a fast, reliable test of `order-service` if it needs a *second service* to be up, healthy, and holding the right data. This topic is how to test one service **in isolation**: replace its downstream with a **stub** — a real HTTP server (WireMock) programmed with canned responses — so the service's real client code runs, its success and failure paths are all reachable, and the test fails only when *your* code breaks.

The demo tests `order-service` with `inventory-service` never started, and shows why stubbing the wire is not the same as mocking the client object.

---

## The problem this solves

`order-service` can't decide an order without asking `inventory-service` (it owns no stock data — topic 04). So how do you test `order-service`? Three bad options and one good one:

- **Test against the real `inventory-service`.** Now your `order-service` test needs a second service running, with a database seeded to exactly the stock levels your assertions expect. It's slow, it's flaky (the other service restarts, its data drifts), and a green/red result no longer tells you whose code is broken. That's an *integration* test — valuable in small numbers, wrong as your default.
- **Skip the call in tests.** Stub out the whole "check inventory" step so the test doesn't make it. Then you've tested a code path that doesn't exist in production — the call that actually fails in prod is the one you didn't run.
- **Mock the client object** (Mockito). Replace `InventoryClient` with a fake that returns whatever you tell it. Fast and isolated — but it *deletes* the real client, so the HTTP layer (the URL, JSON parsing, status handling) is never exercised. More on why that matters below.
- **Stub the downstream's HTTP** (WireMock). Stand up a real HTTP server on a local port, program it with the responses `inventory-service` *would* give, and point `order-service`'s real client at it. The real call runs end to end against a fake you control. This is the service-in-isolation test.

## How it works: stub the wire, drive the service

WireMock is an HTTP server you configure in the test. You tell it "when you get `GET /api/inventory/SKU-BOOK`, reply `200 {"available":5}`," then run the real `OrderService`, whose real `InventoryClient` makes a real HTTP request that WireMock answers:

```java
@RegisterExtension
static WireMockExtension inventory = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())   // real server, random free port
        .build();

@Test
void confirmsAnOrderWhenInventoryReportsEnoughStock() {
    inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK"))
            .willReturn(okJson("{\"sku\":\"SKU-BOOK\",\"available\":5}")));

    Order order = new OrderService(
            InventoryClients.pointingAt(inventory.baseUrl(), Duration.ofSeconds(2)))
            .placeOrder("SKU-BOOK", 2);

    assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    inventory.verify(exactly(1), getRequestedFor(urlEqualTo("/api/inventory/SKU-BOOK")));
}
```

Two things a stub gives you that the real downstream doesn't:

- **Every response is reproducible on demand — including the failures.** A real `inventory-service` is hard to make return a 500, or respond slowly, or 404 a SKU, exactly when your test wants it. WireMock does all three trivially — `serverError()`, `withFixedDelay(1500)`, `notFound()` — so the failure-handling code you wrote in topics 09–11 is finally *testable*. The demo drives all of them: a 500 becomes a controlled `InventoryUnavailableException`, and a slow response plus a tight read timeout proves the timeout trips instead of hanging.
- **You can assert on the request your service made.** `verify(getRequestedFor(...))` checks that `order-service` actually called the right method and path — that its client is wired correctly, not just that it returned the right answer.

## Mock the object vs. stub the wire

Both isolate `order-service`. They are not equivalent, and the difference is the whole lesson.

**Mocking the object** (`when(inventoryClient.getStock("X")).thenReturn(...)`) replaces the client with a fake. It's perfect for testing *decision logic* — "given 5 units and an order for 2, confirm" — fast and with no network. But it **deletes the real client**, so nothing about the HTTP integration runs: a wrong URL in the `@GetExchange`, a JSON field the DTO doesn't map, a 5xx you forgot to handle — the mock sees none of it, because you hand it a Java object directly and skip the wire.

Worse, a mock happily returns *your guess* of how the downstream behaves. The demo makes this concrete: mock the client to throw a bare `RuntimeException` on failure and the test passes — but the real client throws `HttpServerErrorException` on a 500, and `OrderService`'s `catch (RestClientException)` only fires for the real thing. The mock let a false assumption sail through green:

```java
// MOCK: we pretend a failed call throws RuntimeException. It doesn't. The mock can't tell us.
when(fake.getStock("SKU-BOOK")).thenThrow(new RuntimeException("boom"));
// -> OrderService never wraps it; the RuntimeException leaks. Test is green and wrong.

// STUB: a real 500 makes the REAL client throw HttpServerErrorException (a RestClientException),
// so OrderService's catch fires and translates it. Only the stub exercised the true path.
inventory.stubFor(get(urlEqualTo("/api/inventory/SKU-BOOK")).willReturn(serverError()));
// -> InventoryUnavailableException, as production would do.
```

Rule of thumb: **mock the object to test your logic; stub the wire to test your integration.** When the thing under test *is* the HTTP interaction (which URL, which status, which JSON), a mock tests nothing about it. Use Mockito for the pure decision branches and WireMock for the calls that cross the boundary.

## Where this sits in the test pyramid

For a microservice, tests stack roughly like this — many cheap ones, few expensive ones:

- **Unit tests** — pure logic, no I/O (`OrderService` decision branches with a mocked client). Milliseconds, run constantly.
- **Service / component tests** — *this* topic. One service in isolation, its downstreams stubbed (WireMock). Exercises the real HTTP client and the real controller/service wiring without needing the rest of the system. Fast enough to run on every change, real enough to catch integration bugs a mock hides.
- **Integration tests** — the service against a real dependency it *owns*, usually its database via Testcontainers (a real H2/Postgres in a container). A handful, because they're slower.
- **End-to-end tests** — the whole system, all services up. The fewest: slow, flaky, expensive to maintain. Enough to smoke-test critical journeys, not your primary coverage.

The service test is the sweet spot for microservices: it's where you get confidence that `order-service` talks to `inventory-service` correctly, without paying for the whole system to be running.

## What a stub can't catch

A stub is **your belief about the downstream's contract, frozen in the test.** You wrote `willReturn(okJson("{...\"available\":5}"))` because that's what you *think* `inventory-service` returns. If the real `inventory-service` team renames `available` to `availableUnits`, or changes a 404 to a 200-with-empty-body, **your stub keeps returning the old shape and every test here stays green — while production breaks.** The stub can't know the real service changed; it only knows what you told it.

That's the ceiling of isolation testing, and it's exactly the gap **contract testing** (topic 21) closes: a consumer-driven contract makes the *consumer's* expectation and the *provider's* actual behaviour verify against a shared contract, so a breaking change on either side fails a test instead of production. Stub for "does my service handle this response correctly"; contract-test for "is this still the response the other service actually gives."

## When to use it / when not to

- **Stub the downstream for essentially every test of a service that calls another.** It's the default way to test a microservice: fast, isolated, and it exercises the real client. WireMock (or Spring Cloud Contract's WireMock, or MockWebServer) is the standard tool.
- **Use a mock (not a stub) for the pure-logic branches** where no HTTP is involved — it's lighter and clearer. Reserve WireMock for the calls that actually cross the wire.
- **Keep a few real integration/end-to-end tests**, but don't make them your default — they're slow and flaky, so they earn their place only for critical paths and for the wiring a stub can't reach (real serialization against a real service, real DB).
- **Don't stub your own database.** A stub is for a *remote service* you don't own in this test. Your service's own database is an integration concern — use Testcontainers (topic mentions it, module 14+ goes deeper), not a hand-rolled HTTP stub.

## Common pitfalls

- **Mocking the client object when the bug lives in the HTTP layer.** The mock passes; the wrong URL, unmapped JSON field, or unhandled 5xx ships. If the interaction *is* the thing under test, stub the wire.
- **Testing only the happy path.** The reason to stub is that failures are now easy to reproduce — a 500, a timeout, a 404, a malformed body. If your service test never exercises them, your resilience code (topics 09–11) is untested.
- **Letting the stub drift from reality.** A stub that returns a shape the real service no longer sends gives false confidence forever. That's the topic-21 gap — don't mistake green isolation tests for "the two services agree."
- **Requiring the real downstream in a "unit" test.** If your fast test needs a second service up, it isn't a unit or service test — it's a fragile integration test mislabelled. Stub the downstream.
- **A stub with no request matching.** `any-url → 200 OK` will answer a wrong-path request too, hiding a client bug. Match the exact method and path (and assert it with `verify`), so a mis-wired call fails instead of being silently answered.
- **Hard-coding the stub's port.** Bind to a fixed port and tests collide in CI. Let WireMock pick a dynamic port and read `baseUrl()` back.

---

## Code examples

Runnable demo — `20-testing-microservices/service-isolation-demo/`. Read then `mvn test`:

1. `service-isolation-demo/src/main/java/com/example/ordersvc/InventoryClient.java` — the real declarative HTTP client (topic 05); this is the code a stub exercises and a mock skips.
2. `service-isolation-demo/src/main/java/com/example/ordersvc/OrderService.java` — the service under test: ask inventory, then confirm / reject / raise a controlled error.
3. `service-isolation-demo/src/test/java/com/example/ordersvc/OrderServiceWireMockTest.java` — stub the downstream and drive all five branches (enough stock, too few, 404, 500, slow), plus `verify` the outbound request.
4. `service-isolation-demo/src/test/java/com/example/ordersvc/MockVsStubTest.java` — the contrast: mocking the object validates logic but lets a false assumption pass; stubbing the wire reveals the real error handling.

## Try this yourself

1. In `OrderServiceWireMockTest`, add a stub that returns a **malformed body** (`aResponse().withStatus(200).withBody("not json")`) and see what `OrderService` does. Decide whether a parse failure should be `InventoryUnavailableException` or something else, and make the behaviour deliberate.
2. Simulate the topic-21 gap: change one stub to return `"availableUnits"` instead of `"available"` (pretend the downstream renamed the field). Watch a should-be-confirmed order get rejected because `StockResponse.available()` is now 0 — then note that *nothing forced you* to update the stub, which is why a contract test is needed.
3. Add a `verify` that asserts `order-service` sent no `Authorization` header (or, tying in topic 16, that it *did* propagate one). Notice this is a request-side assertion a mock of the client object can't make.

## Self-check

1. Why is testing `order-service` against a running `inventory-service` a poor default, and what does stubbing the downstream give you instead?
2. You can isolate a service by mocking the client object *or* by stubbing its HTTP with WireMock. What does the stub exercise that the mock does not, and when would you still prefer the mock?
3. Your service's isolation tests are all green. Name a change to the real downstream that would break production while leaving every one of those tests passing — and say which kind of test catches it.
