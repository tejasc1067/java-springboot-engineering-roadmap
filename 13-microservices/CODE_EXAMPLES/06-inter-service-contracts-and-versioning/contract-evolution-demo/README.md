# contract-evolution-demo

What happens to a consumer (order-service) when a provider (inventory-service)
changes the JSON it returns. Contract evolution is really JSON (de)serialization
compatibility, so this demo uses Jackson directly — the same library Spring uses.

`ContractEvolutionTest` shows, all green:

- **baseline** — the v1 payload parses.
- **tolerant reader survives an additive change** — provider adds `warehouse`; a
  consumer that ignores unknown fields keeps working. (Adding fields is non-breaking.)
- **strict reader crashes on the same additive change** — a consumer configured to
  fail on unknown fields turns a safe change into a crash. (Be a tolerant reader;
  Spring Boot's default ObjectMapper already is.)
- **rename is a silent break** — provider renames `available` → `availableQty`; the
  consumer's primitive `int` defaults to `0`, no exception. order-service now thinks
  everything is out of stock.
- **boxed type makes the missing field detectable** — the same rename against an
  `Integer` yields `null`, which the consumer can check for.

## Run

```bash
mvn -q test
```
