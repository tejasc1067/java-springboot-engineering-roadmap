package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void encodeAndDecodeRoundTrip() {
        assertThat(Base62.encode(0)).isEqualTo("0");
        assertThat(Base62.encode(61)).isEqualTo("z");
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(125_000_000_000L)).hasSize(7);
        for (long n : new long[] {0, 1, 61, 62, 3_843, 999_999_999_999L}) {
            assertThat(Base62.decode(Base62.encode(n))).isEqualTo(n);
        }
    }
}
