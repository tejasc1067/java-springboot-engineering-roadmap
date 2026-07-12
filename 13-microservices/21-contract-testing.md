# Contract Testing

Topic 20 ended on a warning: a stub is *your* belief about the downstream, frozen in a test. When you hand-wrote `willReturn(okJson("{...\"available\":5}"))`, you asserted that `inventory-service` returns a field called `available` — but nothing checks that belief against the real `inventory-service`. If its team renames the field, your `order-service` tests stay green while production breaks. **Contract testing** closes that gap: the consumer's expectation and the provider's real behaviour are pinned to a single shared **contract**, and each side verifies against it on its own build — so a breaking change fails a test on one side or the other, not a customer's order.

The demo is two modules — `inventory-provider` and `order-consumer` — sharing one contract, with Spring Cloud Contract generating a provider verification test *and* the consumer's stub from it.

---

## The problem this solves

You have two independently deployed services and a stub in between. Three ways to find out the contract broke, in increasing order of how late and expensive the discovery is:

- **Never (the topic-20 trap).** The consumer's hand-written stub still returns the old shape, so the consumer's tests pass. The provider's tests pass too — it never sees the consumer's expectations. Both green, production broken. Nobody's build tells you.
- **In an end-to-end test.** Stand up both services and run the real flow. This *does* catch it — but it's slow, flaky, needs both services deployed together, and it fails *after* both were built and merged, so it points at "the system is broken," not "this change broke it."
- **On the build that made the change.** A contract test makes the provider's build fail the moment it stops honouring the contract, and makes the consumer's build fail if it expects something the contract doesn't promise. The break is caught on the side that caused it, at unit-test speed, before merge.

Contract testing buys the third. It's not a replacement for a couple of end-to-end smoke tests; it's what lets you *not* rely on them to catch every field rename.

## How it works: one contract, two generated artifacts

A **contract** is a request→response pair both sides agree on. From the demo (Groovy DSL):

```groovy
Contract.make {
    description "a known sku returns 200 with its available stock"
    request  { method GET(); url "/api/inventory/SKU-BOOK" }
    response {
        status OK()
        headers { contentType(applicationJson()) }
        body(sku: "SKU-BOOK", available: 5)
    }
}
```

Spring Cloud Contract turns that single file into **two** things:

1. **A provider verification test** (generated at build time). It fires the contract's request at the *real* provider and asserts the contract's response:

   ```java
   // generated ContractVerifierTest — runs against the real InventoryController
   .get("/api/inventory/SKU-BOOK");
   assertThat(response.statusCode()).isEqualTo(200);
   assertThatJson(parsedJson).field("['available']").isEqualTo(5);
   ```

   Rename `available` on the provider and this generated test fails — **the provider's own build goes red.** The demo proves it: `mvn -pl inventory-provider clean test` after the rename is a `BUILD FAILURE` with `Parsed JSON [{...\"availableUnits\":5}] doesn't match the JSON path [$[?(@.['available'] == 5)]]`.

2. **A stub** (a WireMock mapping, packaged into a `-stubs.jar`). The consumer runs against *this*, not a hand-written one. Because it's generated from the same contract the provider is verified against, it can't drift from what the provider actually produces.

On the consumer side, **Stub Runner** downloads that stubs jar, boots a WireMock server from it, and the real `order-service` client calls it:

```java
@RegisterExtension
static StubRunnerExtension stubRunner = new StubRunnerExtension()
        .downloadStub("com.example", "inventory-provider", "1.0.0", "stubs")
        .stubsMode(StubRunnerProperties.StubsMode.LOCAL);   // from ~/.m2, no network

// ...point the real InventoryClient at stubRunner.findStubUrl(...) and drive OrderService
```

The demo's consumer test confirms an order against the contract stub, rejects one for too many units, and rejects an unknown SKU via the contract's 404 — the same behaviours topic 20 tested, but now against a stub that is *guaranteed* to match a provider that's verified against the same contract.

## What "consumer-driven" means

The contract can be authored from either end, and the direction is the distinction:

- **Consumer-driven** (the point of the name): the *consumers* express what they actually need — "I call `GET /api/inventory/{sku}` and read `sku` and `available`" — and the provider must satisfy the union of all its consumers' contracts. The provider learns, from failing tests, exactly which consumers a change would break *before* shipping it. A field no consumer's contract mentions is safe to change; one that a contract depends on is not.
- **Provider-driven**: the provider publishes its contract and consumers adapt. Useful, but it doesn't tell the provider who depends on what.

Mechanically the demo keeps the contracts in the provider module (the standard Spring Cloud Contract layout, and simplest to build). "Consumer-driven" is then a *workflow*: the consumer team authors or co-owns those contracts (often via a pull request to the provider, or a shared contracts repo), so the provider's build carries its consumers' real expectations. The tooling is the same; who decides what goes in the contract is the difference.

## Contract testing vs. the topic-20 stub

They look similar — both let the consumer test without the real provider running — but they cover different failures:

- **Topic 20's stub** tests *your handling of a response you assume*. It catches your bugs (wrong URL, unmapped field, mishandled 5xx). It cannot catch the provider changing, because you wrote the stub.
- **A contract stub** is generated from a contract the provider is *also* verified against. It catches your bugs too, **and** the provider drifting away from the contract — because that drift fails the provider's build. The stub and the provider can't disagree without someone's build going red.

So the honest summary: use topic 20's approach for the consumer-internal handling, and add contract testing when you need the guarantee that the two services still *agree*.

## When to use it / when not to

- **Use it between independently deployed services owned by different teams / release cycles** — especially a provider with several consumers, where "who will this change break?" is otherwise unanswerable without deploying everything.
- **Run the provider verification in CI**, or it's pointless. If only the consumer runs against the stubs and nobody checks the provider still honours the contract, you've rebuilt a topic-20 stub with extra steps. The value is that *both* sides bind to the contract.
- **You may not need it when one team owns both sides** and deploys them together — a shared integration test may be simpler. The cost/benefit tips toward contracts as the number of independent consumers and independent deploys grows.
- **It tests the contract — shape, status, protocol — not business correctness.** A contract says "returns `available` as a number"; it does not say the number is *right*. Keep your normal unit/service tests for logic; contracts are for the interface between services.

## Common pitfalls

- **Only the consumer runs the stubs; the provider never verifies.** Then the contract is just a hand-written stub again — the provider can drift freely. Provider verification is the half that makes it a *contract*.
- **Over-specifying the contract.** Pinning every field, exact header, and incidental value makes the provider's test fail on harmless changes and couples the services too tightly. Assert what the consumer actually depends on; use matchers for the rest.
- **The provider base class doesn't set up state.** The generated test runs against the real controller; if its collaborators aren't stubbed to return the contract's data, the generated test fails for the wrong reason. The base class's job is to make the provider produce the contracted response.
- **Not versioning / publishing the stubs.** The consumer resolves stubs by coordinates (`com.example:inventory-provider:+:stubs`); if the provider never publishes them (here, `mvn install`), or versions drift, the consumer runs against nothing or something stale.
- **Treating it as end-to-end coverage.** It verifies the interface, not the running system. You still want a thin layer of real end-to-end smoke tests.
- **`mvn test` after editing a `record`/DTO passing when it shouldn't.** Maven's incremental compiler can skip recompiling a changed record; use `clean` when you're deliberately testing a provider change.

---

## Code examples

Runnable demo — `21-contract-testing/contract-testing-demo/` (two modules). From that directory, `mvn clean install` (the reactor builds the provider first so its stubs jar is published before the consumer resolves it):

1. `contract-testing-demo/inventory-provider/src/test/resources/contracts/shouldReturnStockForKnownSku.groovy` — the shared contract; also `shouldReturn404ForUnknownSku.groovy`.
2. `contract-testing-demo/inventory-provider/src/main/java/com/example/inventory/InventoryController.java` — the real provider behaviour the contract pins.
3. `contract-testing-demo/inventory-provider/src/test/java/com/example/inventory/ContractVerifierBase.java` — the base class the *generated* provider tests extend (puts the provider in a known state).
4. `contract-testing-demo/order-consumer/src/test/java/com/example/ordersvc/OrderServiceContractTest.java` — the consumer running the real `OrderService` client against the contract-generated stubs via Stub Runner.

## Try this yourself

1. Rename `available` → `availableUnits` in the provider's `StockDto`, then run `mvn -pl inventory-provider clean test`. Watch the generated contract test fail — the breaking change is caught on the provider's build, exactly where topic 20's stub was blind. Revert it.
2. Add a field the consumer needs to the contract (e.g. `warehouse: "EU-1"`) but *don't* add it to the provider's `StockDto`. Run the provider build and watch it fail — the contract now demands something the provider doesn't return, which is the consumer-driven signal "you owe me this field."
3. Add a third contract for a *new* endpoint the consumer wants (`GET /api/inventory/{sku}/reserved`). Note that the provider build now fails until someone implements it — the contract drove a provider change, which is consumer-driven design in action.

## Self-check

1. Your consumer's isolation tests (topic 20) are all green, but production breaks when `inventory-service` renames a field. Which build would a contract test have turned red, and why?
2. What two artifacts does Spring Cloud Contract generate from a single contract, and how does each one keep a different side of the relationship honest?
3. What does "consumer-driven" add over a provider simply publishing its API — and what must run in CI for a contract test to be worth more than a hand-written stub?
