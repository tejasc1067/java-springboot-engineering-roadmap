package com.example.contracts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens to order-service (the CONSUMER) when inventory-service (the PROVIDER)
 * changes the JSON it returns. The provider's payloads are just strings here; the
 * consumer deserializes them into its own StockResponse.
 *
 * Takeaways:
 *  - Additive change (new field) is SAFE — but only for a tolerant reader.
 *  - A strict reader turns a safe additive change into a crash.
 *  - A rename/removal is a BREAKING change, and with primitive fields it breaks
 *    SILENTLY (wrong value, no error) — the most dangerous kind.
 */
class ContractEvolutionTest {

    // Spring Boot configures its ObjectMapper this way by default: ignore unknown fields.
    // This is the "tolerant reader" — be liberal in what you accept.
    private final ObjectMapper tolerant = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Plain Jackson defaults to FAIL_ON_UNKNOWN_PROPERTIES = true — a strict reader.
    private final ObjectMapper strict = new ObjectMapper();

    private static final String V1 =
            "{\"sku\":\"SKU-BOOK\",\"available\":5}";
    private static final String V1_PLUS_NEW_FIELD =
            "{\"sku\":\"SKU-BOOK\",\"available\":5,\"warehouse\":\"EU-1\"}"; // additive
    private static final String RENAMED_FIELD =
            "{\"sku\":\"SKU-BOOK\",\"availableQty\":5}";                     // breaking rename

    @Test
    void baselineContractParses() throws Exception {
        StockResponse stock = tolerant.readValue(V1, StockResponse.class);
        assertThat(stock.available()).isEqualTo(5);
    }

    @Test
    void tolerantReaderSurvivesAnAdditiveChange() throws Exception {
        // Provider added "warehouse". A tolerant consumer ignores the unknown field
        // and keeps working — this is why adding fields is a non-breaking change.
        StockResponse stock = tolerant.readValue(V1_PLUS_NEW_FIELD, StockResponse.class);
        assertThat(stock.available()).isEqualTo(5);
    }

    @Test
    void strictReaderCrashesOnTheSameAdditiveChange() {
        // Same safe payload, but a strict consumer rejects the unknown field.
        // This is why you must configure a tolerant reader (Spring Boot does by default).
        assertThatThrownBy(() -> strict.readValue(V1_PLUS_NEW_FIELD, StockResponse.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void renameIsASilentBreakWithPrimitiveFields() throws Exception {
        // Provider renamed available -> availableQty. The consumer's "available"
        // field is now absent from the JSON, so a primitive int defaults to 0.
        // No exception. order-service now thinks EVERYTHING is out of stock.
        StockResponse stock = tolerant.readValue(RENAMED_FIELD, StockResponse.class);
        assertThat(stock.available()).isZero(); // silently wrong, not an error
    }

    @Test
    void boxedTypeMakesTheMissingFieldDetectable() throws Exception {
        // The same rename against a boxed Integer yields null, which the consumer
        // can check for instead of trusting a bogus 0.
        StockResponseNullable stock = tolerant.readValue(RENAMED_FIELD, StockResponseNullable.class);
        assertThat(stock.available()).isNull();
    }
}
