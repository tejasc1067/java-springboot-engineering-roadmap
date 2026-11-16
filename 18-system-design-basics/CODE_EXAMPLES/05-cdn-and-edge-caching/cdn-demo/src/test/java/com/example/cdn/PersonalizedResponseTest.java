package com.example.cdn;

import com.example.cdn.EdgeCache.Request;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalizedResponseTest extends EdgeTestBase {

    @Test
    void broken_aPlainMaxAge_letsTheEdgeServeAnnsAccountToBob() {
        assertThat(edge.get(as("ann", "/broken/account")).body()).contains("\"user\":\"ann\"");

        String bobSees = edge.get(as("bob", "/broken/account")).body();

        assertThat(bobSees).contains("\"user\":\"ann\"", "ann's address");   // a data leak
        assertThat(shop.renders.get()).isEqualTo(1);                       // the app never saw Bob
    }

    @Test
    void fixed_noStore_everyUserGetsTheirOwnResponse_andTheEdgeKeepsNothing() {
        assertThat(callOrigin(as("ann", "/account")).header("Cache-Control")).isEqualTo("no-store");

        edge.get(as("ann", "/account"));
        String bobSees = edge.get(as("bob", "/account")).body();

        assertThat(bobSees).contains("\"user\":\"bob\"");
        assertThat(edge.hits).isZero();
    }

    @Test
    void theAuthorizationRule_protectsTokenRequests_butNotCookies() {
        // Same endpoint, same max-age=60. With credentials in Authorization the edge won't share it.
        // This test only calls the edge with an Authorization header; the endpoint still reads the cookie.
        Request annWithToken = new Request("/broken/account",
                Map.of("Cookie", "session=ann", "Authorization", "Bearer ann-token"));
        edge.get(annWithToken);
        edge.get(annWithToken);

        assertThat(edge.hits).isZero();
        assertThat(shop.renders.get()).isEqualTo(2);
    }
}
