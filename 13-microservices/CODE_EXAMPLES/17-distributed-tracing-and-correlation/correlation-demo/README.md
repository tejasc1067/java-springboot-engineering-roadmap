# correlation-demo

Distributed tracing at its core: one shared id per request, carried in the logging
context and forwarded across every service hop, so a single request is one id everywhere.
Built by hand (a filter + the SLF4J MDC + an outbound interceptor) so it's observable and
testable. Production uses Micrometer Tracing to do all this automatically — see the topic
markdown.

`CorrelationPropagationTest` (boots order-service with a stub inventory behind it):
- **generates an id when none is supplied and propagates it** — order-service assigns an
  id and inventory-service receives the SAME one.
- **reuses an upstream id** — an inbound `X-Correlation-Id` (as a gateway would set) flows
  through order-service to inventory-service unchanged.

`CorrelationIdFilterTest` (the filter in isolation):
- generates an id when absent, reuses one when present, exposes it in the MDC during the
  request, and clears the MDC afterwards.

## Run the tests

```bash
mvn -q test        # Tests run: 4, green
```

## See the id in the logs

```bash
# terminal 1 — the scaffold's inventory-service (../../order-inventory-system)
cd ../../order-inventory-system/inventory-service && mvn -q spring-boot:run   # :8081

# terminal 2 — this order-service
mvn -q spring-boot:run                                                        # :8080

curl -i localhost:8080/api/order/SKU-BOOK
# response header: X-Correlation-Id: <uuid>
# order-service log:  HH:mm:ss.SSS [<uuid>] INFO ... - handling order for SKU-BOOK
#   -> every log line for this request carries the id; grep it to see the whole request.
```
