package com.example.cdn;

import com.example.cdn.EdgeCache.Request;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VaryTest extends EdgeTestBase {

    static Request accepting(String mediaType, String path) {
        return new Request(path, Map.of("Accept", mediaType));
    }

    @Test
    void springDoesNotAddVaryForYou() {
        assertThat(callOrigin(accepting("text/csv", "/broken/prices/7")).header("Vary")).isNull();
    }

    @Test
    void broken_withoutVary_theCsvClientGetsTheJsonThatWasCachedFirst() {
        edge.get(accepting("application/json", "/broken/prices/7"));

        String csvClientSees = edge.get(accepting("text/csv", "/broken/prices/7")).body();

        assertThat(csvClientSees).startsWith("{\"id\":7");
    }

    @Test
    void fixed_varyAccept_keepsOneEntryPerFormat() {
        edge.get(accepting("application/json", "/prices/7"));

        assertThat(edge.get(accepting("text/csv", "/prices/7")).body()).startsWith("id,price");
        assertThat(edge.get(accepting("application/json", "/prices/7")).body()).startsWith("{\"id\":7");
        assertThat(edge.get(accepting("text/csv", "/prices/7")).body()).startsWith("id,price");
        assertThat(shop.renders.get()).isEqualTo(2);   // one render per format, then hits
    }
}
