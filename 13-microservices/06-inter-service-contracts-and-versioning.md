# Inter-Service Contracts and Versioning

A synchronous call (topic 05) works only because both sides agree on the shape of the message: inventory-service returns `{sku, available}` and order-service knows how to read it. That agreement is a **contract**. The hard part isn't defining it once — it's *changing* it later, when the two services are owned by different teams, deployed at different times, and can never be updated in the same instant (fallacy 6, "there is one administrator"). This topic is how a provider evolves its API without breaking every consumer, and how a consumer protects itself from a provider that changes underneath it.

---

## The problem this solves

inventory-service wants to improve its API — add a field, rename a confusing one, restructure the response. In a monolith this is a safe IDE refactor: change the method, the compiler finds every caller. Across services there is **no compiler that sees both sides**. order-service is a separate deployment that was compiled against the *old* shape and is running *right now*. The instant inventory ships an incompatible change, order-service starts failing — and because there's no shared build, nobody got a red compile to warn them.

Worse, the two services are never in lockstep. During any deploy there's a window where inventory is on the new version and order is still on the old (or vice versa). So the real requirement is stronger than "don't break consumers": **old and new versions must interoperate during the rollover.** A change that only works if everyone upgrades simultaneously is a change you cannot safely deploy.

## How it works: breaking vs. non-breaking changes

The core distinction. A change to a provider's response is **non-breaking** if a consumer compiled against the old shape still works against the new one:

**Non-breaking (safe) — add, don't disturb:**
- Adding a new field to a response. Old consumers ignore it (*if* they're tolerant readers — see below).
- Adding a new optional field to a request. Old callers who omit it still work, provided the server has a sensible default.
- Adding a whole new endpoint. Nobody was calling it.

**Breaking (unsafe) — anything that invalidates the old shape:**
- Removing or renaming a field. Consumers reading it get nothing (often *silently*).
- Changing a field's type (`int` → `string`, scalar → object) or its units/semantics (cents → dollars).
- Making a previously optional request field required.
- Changing an endpoint's URL, method, or status-code meaning.
- Tightening validation so previously accepted requests now 400.

The rule of thumb: **you may add; you may not remove, rename, or repurpose.** Removals and renames require versioning or the expand-contract dance below.

## The consumer's job: be a tolerant reader

Half of compatibility is the consumer's responsibility. A **tolerant reader** (Postel's Law: "be conservative in what you send, liberal in what you accept") reads only the fields it needs and ignores everything else. The demo (`contract-evolution-demo`) shows why this matters, with Jackson:

```java
// Provider added a "warehouse" field. A tolerant reader ignores the unknown field:
ObjectMapper tolerant = new ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false);   // Spring Boot's default
tolerant.readValue("{\"sku\":\"SKU-BOOK\",\"available\":5,\"warehouse\":\"EU-1\"}",
                   StockResponse.class);                  // works: available = 5
```

```java
// A STRICT reader rejects the exact same safe payload:
ObjectMapper strict = new ObjectMapper();                 // FAIL_ON_UNKNOWN_PROPERTIES = true
strict.readValue(sameJsonWithWarehouse, StockResponse.class);
// throws UnrecognizedPropertyException — an additive change just crashed a consumer
```

Spring Boot configures its `ObjectMapper` to ignore unknown properties by default, so Spring consumers are tolerant readers out of the box — but only if you don't override it. A team that sets `FAIL_ON_UNKNOWN_PROPERTIES=true` "to be safe" has actually made every additive provider change a breaking one for themselves. Don't.

The flip side — a consumer should also depend on as *few* fields as possible. Reading a field you don't use is a coupling you didn't need; the day the provider drops it, you break for no reason.

## The silent break (why renames are the worst)

Removing or renaming a field usually doesn't throw — it does something more dangerous. From the demo, when inventory renames `available` → `availableQty`:

```java
// consumer field is a primitive int; the JSON no longer has "available"
StockResponse stock = tolerant.readValue("{\"sku\":\"SKU-BOOK\",\"availableQty\":5}",
                                         StockResponse.class);
stock.available();   // == 0, NOT 5. No exception. order-service thinks it's out of stock.
```

The field defaults to `0` and the consumer sails on with a wrong value. A silent wrong answer is worse than a crash: nothing alerts, orders quietly get rejected, and you find out from angry customers. One defense is boxed types — the same rename against an `Integer available` yields `null`, which the consumer can *detect*:

```java
StockResponseNullable stock = tolerant.readValue(sameRenamedJson, StockResponseNullable.class);
stock.available();   // == null -> the consumer can reject/alert instead of trusting a bogus 0
```

The deeper defense is not renaming across a live boundary at all — use expand-contract.

## Expand and contract (how to make a "breaking" change safely)

You cannot rename `available` to `availableQty` in one step without breaking consumers mid-rollout. You do it in three, so old and new coexist at every moment:

1. **Expand.** Provider adds the new field *alongside* the old: respond with **both** `available` and `availableQty` (same value). Old consumers read `available`; nothing breaks. Deploy the provider.
2. **Migrate.** Each consumer team, on their own schedule, switches to reading `availableQty`. No coordination, no big-bang.
3. **Contract.** Once telemetry shows no one reads `available` anymore, the provider removes it. Deploy.

At no point is there a version of the provider that a live consumer can't talk to. This "parallel change" is the workhorse technique — most "breaking" changes become safe when spread across an expand → migrate → contract sequence. The same pattern works for request fields, splitting a field, or changing a type (add the new-typed field, migrate, remove the old).

## Versioning: when evolution isn't enough

Sometimes the change is too large for expand-contract (a full response restructure), or you must support old clients indefinitely (public/mobile APIs you don't control). Then you **version** the API and run both at once:

- **URI versioning** — `/api/v1/inventory/{sku}` and `/api/v2/inventory/{sku}`. Explicit, trivially routable at the gateway (topic 14), easy to see in logs. Most common for service-to-service.
- **Header / content negotiation** — `Accept: application/vnd.inventory.v2+json`. Keeps URLs stable, but is harder to test and route.

Whichever you pick, versioning is *expensive*: you now run, test, and maintain N versions of the endpoint, and every bug fix may need backporting. So version only when you must, and treat it as a commitment to eventually deprecate and retire old versions (announce, monitor usage, sunset). Prefer additive evolution and expand-contract; reach for a new version number only when the shape genuinely can't evolve in place. (Module 09 covered API versioning for a single service's public API; here the same tools serve *service-to-service* evolution, where the callers are other teams' services.)

## When to use it / when not to

- **Every provider change** gets classified: additive (ship it) vs. breaking (expand-contract, or a new version). Make this a habit, not a case-by-case debate.
- **Version** only for changes too large to evolve, or for clients you can't coordinate with (public APIs, mobile apps). Internal service-to-service almost always evolves in place.
- **Don't** create `/v2` for an additive change — that's a maintenance burden with no benefit. Additive changes don't need a version bump.
- **Don't** rely on "we'll just deploy both at the same time." At scale you can't; design so you never have to.

## Common pitfalls

- **Turning off tolerant reading.** Setting `FAIL_ON_UNKNOWN_PROPERTIES=true` makes every additive provider change break you. Leave Spring Boot's tolerant default alone.
- **Renaming a field in place.** The silent-break demo. Use expand-contract; never rename across a live boundary in one step.
- **Primitive fields hiding missing data.** An `int` that defaults to `0` masks a dropped field. Use boxed types (or explicit presence checks) for fields whose absence must be noticed.
- **Reading fields you don't use.** Every field you deserialize-and-depend-on is coupling. Map only what you need.
- **Changing semantics without changing the shape.** Switching a `price` field from dollars to cents keeps the JSON identical but breaks every consumer silently. A semantic change is a breaking change even when the type doesn't move — version it or add a new field.

---

## Code examples

Runnable demo — `06-inter-service-contracts-and-versioning/contract-evolution-demo/`. Read then `mvn test`:

1. `contract-evolution-demo/src/main/java/com/example/contracts/StockResponse.java` — the consumer's model, with a primitive `int available` (sets up the silent break).
2. `contract-evolution-demo/src/main/java/com/example/contracts/StockResponseNullable.java` — the safer boxed-`Integer` variant that makes a missing field detectable.
3. `contract-evolution-demo/src/test/java/com/example/contracts/ContractEvolutionTest.java` — the five cases: baseline, tolerant-survives-additive, strict-crashes-on-additive, rename-is-a-silent-break, boxed-type-detects-missing.

Related: the scaffold deliberately gives order-service and inventory-service *separate* `StockResponse` classes (`order-inventory-system/.../order/StockResponse.java` vs `.../inventory/StockResponse.java`) — they share the JSON contract, not a Java class, which is what lets them version independently.

## Try this yourself

1. Run the demo. Then edit the rename test's JSON to *also* keep the old field (`{"sku":"SKU-BOOK","available":5,"availableQty":5}`) — the "expand" step. Confirm the old consumer still reads `available` correctly. You've just made a "breaking" rename non-breaking.
2. Add a test where the provider changes `available` from a number to a string (`"available":"5"`). Does the tolerant reader cope, or is a type change breaking even for a tolerant reader? (It's breaking — tolerance covers unknown fields, not changed types.)
3. In the scaffold, add a new field to inventory's `StockResponse` (e.g. `warehouse`) and redeploy only inventory-service. Confirm order-service — unchanged, unaware of the field — still works. That's an additive change surviving a real service boundary.

## Self-check

1. Classify each as breaking or non-breaking, and say why: (a) adding a `warehouse` field to the response, (b) renaming `available` to `availableQty`, (c) changing `price` from dollars to cents while keeping it a number.
2. Why is a rename often *more* dangerous than an outright removal that throws? What does a "tolerant reader" do, and what does a primitive-vs-boxed field type have to do with catching the problem?
3. inventory-service must rename a field that order-service reads, and the two are deployed independently. Walk the expand-contract sequence that lets you do it without a coordinated deploy.
