# gateway-demo

A single entry point in front of the services, using the **servlet-stack** flavour of
Spring Cloud Gateway (`spring-cloud-starter-gateway-server-webmvc`) so it fits this
roadmap's non-reactive stack.

One route: `/inventory/**` → inventory-service, with the path rewritten
(`/inventory/X` → `/api/inventory/X`) and an `X-Gateway` header added on the way through.

`GatewayRoutingTest` boots the gateway on a random port with a stub downstream and proves:

- the request is **routed** through to the downstream (200, body flows back),
- the path is **rewritten** at the edge (`/inventory/SKU-BOOK` arrives as `/api/inventory/SKU-BOOK`),
- an `X-Gateway` header is **added** at the edge.

## Run the test

```bash
mvn -q test        # Tests run: 1, green
```

## Run it against the scaffold's inventory-service

```bash
# terminal 1 — the real inventory-service from ../../order-inventory-system
cd ../../order-inventory-system/inventory-service && mvn -q spring-boot:run   # :8081

# terminal 2 — the gateway
cd ../14-api-gateway/gateway-demo && mvn -q spring-boot:run                    # :8080

# call the GATEWAY, not inventory directly:
curl localhost:8080/inventory/SKU-BOOK
# -> {"sku":"SKU-BOOK","available":5}   (gateway rewrote the path and forwarded it)
```

Clients only ever talk to `:8080` (the gateway). Auth, CORS, and rate limiting would be
added as more `.before(...)` filters here — once, centrally — instead of in every service.
