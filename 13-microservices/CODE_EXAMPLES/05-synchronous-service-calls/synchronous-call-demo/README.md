# synchronous-call-demo

A focused, self-contained demo of the synchronous service-to-service call, isolated
from the full two-service scaffold so you can see just the call mechanics.

The test stubs "inventory-service" with a tiny in-process HTTP server (JDK's
`com.sun.net.httpserver`, no extra dependency) and drives the same `@HttpExchange`
interface the scaffold uses. It shows:

- the declarative HTTP interface issuing `GET /api/inventory/{sku}` and parsing the JSON,
- the plain `RestClient` making the identical call inline,
- a downstream `404` surfacing as `HttpClientErrorException.NotFound` (one of the four
  outcomes every synchronous call must handle).

## Run

```bash
mvn -q test
```

All three tests pass; no other service needs to be running.

## The full end-to-end version

This demo stubs the downstream. To see two real Spring Boot services actually talking
over the network (`order-service` → `inventory-service`), run the scaffold in
[`../../order-inventory-system`](../../order-inventory-system) instead.
