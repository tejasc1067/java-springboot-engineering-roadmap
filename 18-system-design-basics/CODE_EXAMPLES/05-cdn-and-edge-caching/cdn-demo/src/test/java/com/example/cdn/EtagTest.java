package com.example.cdn;

import com.example.cdn.EdgeCache.Request;
import com.example.cdn.EdgeCache.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EtagTest extends EdgeTestBase {

    @Test
    void shallowEtag_saves10kOfTransfer_butTheReportIsStillBuiltEveryTime() {
        Response first = callOrigin(new Request("/reports/1"));
        String etag = first.header("ETag");
        assertThat(etag).isNotNull();
        assertThat(first.body()).hasSizeGreaterThan(10_000);

        Response again = callOrigin(new Request("/reports/1", Map.of("If-None-Match", etag)));

        assertThat(again.status()).isEqualTo(304);
        assertThat(again.body()).isEmpty();
        assertThat(shop.renders.get()).isEqualTo(2);   // the filter hashes the body after the controller ran
    }

    @Test
    void deepEtag_checkNotModified_answers304_withoutDoingTheWork() {
        Response first = callOrigin(new Request("/catalog/1"));
        assertThat(first.header("ETag")).isEqualTo("\"v1\"");

        Response again = callOrigin(new Request("/catalog/1", Map.of("If-None-Match", "\"v1\"")));

        assertThat(again.status()).isEqualTo(304);
        assertThat(shop.renders.get()).isEqualTo(1);
    }

    @Test
    void noCache_theEdgeStoresIt_butRevalidatesEveryUse() {
        edge.get(new Request("/catalog/1"));
        edge.get(new Request("/catalog/1"));
        edge.get(new Request("/catalog/1"));

        assertThat(edge.revalidations).isEqualTo(2);   // two small 304s instead of two full responses
        assertThat(shop.renders.get()).isEqualTo(1);

        shop.changeProduct();
        assertThat(edge.get(new Request("/catalog/1")).body()).contains("\"version\":2");   // never stale
    }
}
