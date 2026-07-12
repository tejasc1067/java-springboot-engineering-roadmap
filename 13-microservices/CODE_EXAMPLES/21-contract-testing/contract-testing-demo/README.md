# contract-testing-demo

Consumer-driven contract testing with Spring Cloud Contract, across two modules that share **one
contract**. It closes the gap topic 20 left open: a hand-written stub is *your* belief about the
downstream and can drift from reality; a contract keeps both sides honest.

```
contract-testing-demo/
├── inventory-provider/     the provider — contracts live here; SCC generates a verification test
│                           per contract and packages the stubs jar
└── order-consumer/         the consumer — Stub Runner boots those generated stubs; the real
                            order-service client runs against them
```

## The one contract

`inventory-provider/src/test/resources/contracts/*.groovy` — two request→response pairs:

- **known SKU → 200 `{sku, available}`** (`shouldReturnStockForKnownSku`)
- **unknown SKU → 404** (`shouldReturn404ForUnknownSku`)

Each contract generates **both** a provider verification test and a consumer stub. That shared
origin is the whole point.

## Provider side — `ContractVerifierTest` (generated)

The SCC maven plugin generates one JUnit test per contract that fires the contract's request at
the real `InventoryController` and asserts the contract's response. If the provider drifts (rename
`available`, change a status), **the provider's own build goes red** — the breaking change can't
reach a consumer. It also packages `inventory-provider-1.0.0-stubs.jar`.

## Consumer side — `OrderServiceContractTest`

Stub Runner resolves that stubs jar from `~/.m2` (LOCAL mode, no network), boots a WireMock server
from the contract-generated mappings, and runs the **real** `OrderService` client against it:

- enough stock → CONFIRMED
- too few units → REJECTED_INSUFFICIENT_STOCK
- unknown SKU (the contract's 404) → REJECTED_UNKNOWN_SKU

Because the stub is generated from the same contract the provider is verified against, it cannot
silently drift — unlike topic 20's hand-written stub.

## Run

```bash
mvn clean install       # from THIS directory
# inventory-provider ... SUCCESS   (2 generated contract tests, stubs jar installed to ~/.m2)
# order-consumer ...... SUCCESS   (3 tests against the contract stubs)
```

Build order matters: the provider must `install` its stubs jar to `~/.m2` before the consumer
resolves it. The reactor builds `inventory-provider` first, so one `mvn install` from here does
both in order. (The "Mockito self-attaching" / WireMock guava lines are JDK warnings, not failures.)

## Try the failure mode

Rename `available` → `availableUnits` in `inventory-provider/.../StockDto.java`, then:

```bash
mvn -pl inventory-provider clean test    # BUILD FAILURE — the generated contract test catches it
```

> `mvn clean test`, not `mvn test`: Maven's incremental compiler may not recompile a changed
> `record`, so a plain `mvn test` can pass against a stale class. `clean` forces the recompile.

This is what topic 20's stub could not do: the provider breaking its side of the contract fails a
test at build time, not in production.

## A note on the plugin config

The provider pom binds the SCC goals (`convert`, `generateTests`, `generateStubs`) explicitly in
`<executions>` and does **not** use `<extensions>true</extensions>`. The extensions flag auto-binds
those goals but pulls a conflicting `httpclient` into Maven's plugin realm on this toolchain
(`BasicAuthCache cannot be cast to AuthCache`); explicit executions avoid it.
