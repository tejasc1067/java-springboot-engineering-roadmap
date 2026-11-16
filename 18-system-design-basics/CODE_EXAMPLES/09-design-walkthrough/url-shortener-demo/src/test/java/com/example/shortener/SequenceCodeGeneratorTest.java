package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SequenceCodeGeneratorTest {

    /** Anyone holding one code can compute the neighbours and read other people's links. */
    @Test
    void sequentialCodes_areEnumerable() {
        SequenceCodeGenerator sequence = new SequenceCodeGenerator(new AtomicLong(1_000), 100);
        String mine = sequence.generate("https://my.example/report");
        String someoneElses = sequence.generate("https://private.example/salary-review.pdf");

        String guessed = Base62.encode(Base62.decode(mine) + 1);

        assertThat(mine).isEqualTo("Q0u");          // 100,000 in base62: 26*62^2 + 0*62 + 56
        assertThat(guessed).isEqualTo(someoneElses);
    }

    /** Two instances share only the block counter, touched once per 100 codes, and never hand out the same code. */
    @Test
    void instancesWithTheirOwnBlocks_neverCollide() {
        AtomicLong ticketServer = new AtomicLong();
        SequenceCodeGenerator instanceA = new SequenceCodeGenerator(ticketServer, 100);
        SequenceCodeGenerator instanceB = new SequenceCodeGenerator(ticketServer, 100);

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(instanceA.generate("a" + i));
            codes.add(instanceB.generate("b" + i));
        }

        assertThat(codes).hasSize(2_000);
        assertThat(ticketServer.get()).isEqualTo(20);   // 20 blocks of 100, not 2,000 round trips
    }
}
