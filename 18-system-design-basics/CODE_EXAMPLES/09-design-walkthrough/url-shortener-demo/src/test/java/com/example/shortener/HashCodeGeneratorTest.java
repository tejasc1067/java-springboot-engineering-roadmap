package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HashCodeGeneratorTest {

    @Test
    void sameUrl_alwaysGetsTheSameCode() {
        HashCodeGenerator hash = new HashCodeGenerator(7);
        assertThat(hash.generate("https://shop.example/item/1"))
                .isEqualTo(hash.generate("https://shop.example/item/1"))
                .hasSize(7);
    }

    /** With 3 characters there are 238,328 codes; the birthday bound says a collision comes after ~600 URLs. */
    @Test
    void truncatedHash_twoDifferentUrlsShareACode() {
        HashCodeGenerator hash = new HashCodeGenerator(3);
        Map<String, String> urlByCode = new HashMap<>();
        String[] collision = null;
        int i = 0;
        while (collision == null) {
            String url = "https://shop.example/item/" + i++;
            String earlier = urlByCode.putIfAbsent(hash.generate(url), url);
            if (earlier != null) collision = new String[] {earlier, url};
        }
        System.out.println("collision after " + i + " URLs: " + collision[0] + " and " + collision[1]
                + " both -> " + hash.generate(collision[1]));

        assertThat(i).isLessThan(2_000);
        assertThat(hash.generate(collision[0])).isEqualTo(hash.generate(collision[1]));
    }

    /** A hash gives the same code every time, so "retry with another code" can't work without a salt. */
    @Test
    void collidingUrl_cannotBeStoredAtAll() {
        HashCodeGenerator hash = new HashCodeGenerator(3);
        String first = "https://shop.example/item/" + firstCollidingPair(hash)[0];
        String second = "https://shop.example/item/" + firstCollidingPair(hash)[1];
        ShortenerService service = new ShortenerService(hash, new UrlStore(), 302);

        service.shorten(null, first);
        assertThatThrownBy(() -> service.shorten(null, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no free code after 5 attempts");
    }

    @Test
    void shorteningTheSameUrlTwice_returnsTheExistingCode() {
        ShortenerService service = new ShortenerService(new HashCodeGenerator(7), new UrlStore(), 302);
        assertThat(service.shorten(null, "https://shop.example/a")).isEqualTo(service.shorten(null, "https://shop.example/a"));
    }

    private static int[] firstCollidingPair(HashCodeGenerator hash) {
        Map<String, Integer> indexByCode = new HashMap<>();
        for (int i = 0; ; i++) {
            Integer earlier = indexByCode.putIfAbsent(hash.generate("https://shop.example/item/" + i), i);
            if (earlier != null) return new int[] {earlier, i};
        }
    }
}
