package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueLookupTest {

    private final DocumentStore sessions = new DocumentStore();

    @BeforeEach
    void tenThousandSessions() {
        for (int i = 0; i < 10_000; i++) {
            sessions.save("session-" + i,
                    DocumentStore.newDocument().put("userId", "user-" + (i % 500)).put("cart", i % 7));
        }
    }

    @Test
    void lookupByKey_touchesOneEntry() {
        assertThat(sessions.findById("session-4242").get("userId").asText()).isEqualTo("user-242");
        assertThat(sessions.lookups()).isEqualTo(1);
        assertThat(sessions.documentsScanned()).isZero();
    }

    @Test
    void lookupByAnythingElse_readsEveryEntry() {
        var forUser = sessions.findWhere(doc -> doc.get("userId").asText().equals("user-42"));

        assertThat(forUser).hasSize(20);
        // 20 answers, 10,000 documents read. A key-value store is fast for ONE question: "what's under this key?"
        assertThat(sessions.documentsScanned()).isEqualTo(10_000);
    }
}
