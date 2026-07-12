import org.springframework.cloud.contract.spec.Contract

// The consumer's expectation, agreed with the provider: a known SKU returns 200 with the
// {sku, available} body order-service parses. This single file drives BOTH the provider's
// generated verification test AND the stub order-service runs against.
Contract.make {
    description "a known sku returns 200 with its available stock"
    request {
        method GET()
        url "/api/inventory/SKU-BOOK"
    }
    response {
        status OK()
        headers { contentType(applicationJson()) }
        body(
            sku: "SKU-BOOK",
            available: 5
        )
    }
}
