import org.springframework.cloud.contract.spec.Contract

// The other half of the contract: an unknown SKU is a 404, not an error or an empty 200.
// order-service relies on this to distinguish "no such product" from "inventory is down".
Contract.make {
    description "an unknown sku returns 404"
    request {
        method GET()
        url "/api/inventory/NO-SUCH-SKU"
    }
    response {
        status NOT_FOUND()
    }
}
