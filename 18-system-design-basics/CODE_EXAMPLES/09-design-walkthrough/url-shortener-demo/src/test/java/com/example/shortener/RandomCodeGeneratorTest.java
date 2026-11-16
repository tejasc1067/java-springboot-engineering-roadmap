package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RandomCodeGeneratorTest {

    /** Two generators with the same seed produce the same first code: a guaranteed collision on demand. */
    @Test
    void collision_isDetectedByTheStore_andRetriedWithANewCode() {
        UrlStore store = new UrlStore();
        String taken = new RandomCodeGenerator(new Random(42), 7).generate("ignored");
        store.insert(taken, "https://first.example");

        RandomCodeGenerator random = new RandomCodeGenerator(new Random(42), 7);
        AtomicInteger attempts = new AtomicInteger();
        CodeGenerator counting = url -> {
            attempts.incrementAndGet();
            return random.generate(url);
        };
        ShortenerService service = new ShortenerService(counting, store, 302);

        String code = service.shorten(null, "https://second.example");

        assertThat(attempts).hasValue(2);
        assertThat(code).isNotEqualTo(taken);
        assertThat(service.resolve(taken).location()).isEqualTo("https://first.example");   // not overwritten
        assertThat(service.resolve(code).location()).isEqualTo("https://second.example");
    }

    @Test
    void randomCodes_doNotRevealTheirNeighbours() {
        RandomCodeGenerator random = new RandomCodeGenerator(new Random(7), 7);
        String first = random.generate("a");
        String second = random.generate("b");
        assertThat(Math.abs(Base62.decode(second) - Base62.decode(first))).isGreaterThan(1_000_000L);
    }
}
