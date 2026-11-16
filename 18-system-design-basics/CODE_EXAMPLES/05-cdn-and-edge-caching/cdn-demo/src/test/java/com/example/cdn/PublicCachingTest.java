package com.example.cdn;

import com.example.cdn.EdgeCache.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicCachingTest extends EdgeTestBase {

    @Test
    void theAppSaysHowLongTheResponseMayBeCached() {
        assertThat(callOrigin(new Request("/products/1")).header("Cache-Control"))
                .isEqualTo("max-age=60, public, s-maxage=300");
    }

    @Test
    void aThousandRequests_oneOriginRender() {
        for (int i = 0; i < 1_000; i++) edge.get(new Request("/products/1"));

        assertThat(shop.renders.get()).isEqualTo(1);
        assertThat(edge.hits).isEqualTo(999);
    }

    @Test
    void theEdgeUsesSMaxage_notTheBrowsersMaxAge() {
        edge.get(new Request("/products/1"));

        edge.advance(120);   // past max-age=60, inside s-maxage=300
        edge.get(new Request("/products/1"));
        assertThat(shop.renders.get()).isEqualTo(1);

        edge.advance(200);   // 320 s old: expired at the edge too
        edge.get(new Request("/products/1"));
        assertThat(shop.renders.get()).isEqualTo(2);
    }

    @Test
    void aChangeIsInvisibleUntilTheEntryExpires() {
        edge.get(new Request("/products/1"));
        shop.changeProduct();

        assertThat(edge.get(new Request("/products/1")).body()).contains("\"version\":1");   // stale, for up to 5 minutes

        edge.advance(301);
        assertThat(edge.get(new Request("/products/1")).body()).contains("\"version\":2");
    }
}
