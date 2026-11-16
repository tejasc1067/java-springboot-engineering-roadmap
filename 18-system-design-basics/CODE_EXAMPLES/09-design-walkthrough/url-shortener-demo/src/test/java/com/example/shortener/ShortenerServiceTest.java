package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.ShortenerService.Redirect;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ShortenerServiceTest {

    private final UrlStore store = new UrlStore();
    private final ShortenerService service = new ShortenerService(new RandomCodeGenerator(new Random(1), 7), store, 302);

    /** The response to the first POST was lost; the client retries. Without a request id that's a second link. */
    @Test
    void retriedCreate_withoutARequestId_createsADuplicate() {
        String first = service.shorten(null, "https://shop.example/sale");
        String retry = service.shorten(null, "https://shop.example/sale");

        assertThat(retry).isNotEqualTo(first);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void retriedCreate_withTheSameRequestId_returnsTheSameCode() {
        String first = service.shorten("req-7f3a", "https://shop.example/sale");
        String retry = service.shorten("req-7f3a", "https://shop.example/sale");

        assertThat(retry).isEqualTo(first);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void customAlias_isFirstComeFirstServed() {
        service.claimAlias("summer-sale", "https://shop.example/sale");

        assertThatThrownBy(() -> service.claimAlias("summer-sale", "https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.resolve("summer-sale").location()).isEqualTo("https://shop.example/sale");
    }

    /** A viral link: 1,000 redirects, one database read. */
    @Test
    void cacheAside_hotCodeIsReadFromTheStoreOnce() {
        String code = service.shorten(null, "https://news.example/big-story");

        for (int i = 0; i < 1_000; i++) assertThat(service.resolve(code).status()).isEqualTo(302);

        assertThat(store.reads()).isEqualTo(1);
        assertThat(service.cacheHits()).isEqualTo(999);
        assertThat(service.cacheMisses()).isEqualTo(1);
    }

    @Test
    void unknownCode_is404_andIsNotCached() {
        assertThat(service.resolve("nope123").status()).isEqualTo(404);
        assertThat(service.resolve("nope123").status()).isEqualTo(404);
        assertThat(store.reads()).isEqualTo(2);   // every miss reaches the store: penetration, module 15 topic 08
    }

    /** 301 is "moved permanently": browsers cache it and stop asking. 302 brings every click back to you. */
    @Test
    void permanentRedirect_hidesRepeatClicksFromTheServer() {
        assertThat(serverHitsForTenClicks(301)).isEqualTo(1);
        assertThat(serverHitsForTenClicks(302)).isEqualTo(10);
    }

    private int serverHitsForTenClicks(int status) {
        UrlStore urls = new UrlStore();
        ShortenerService shortener = new ShortenerService(new RandomCodeGenerator(new Random(3), 7), urls, status);
        String code = shortener.shorten(null, "https://shop.example/sale");
        Browser browser = new Browser(shortener);
        for (int i = 0; i < 10; i++) browser.click(code);
        return browser.serverHits;
    }

    /** Just enough of a browser: remembers permanent redirects, like real ones do. */
    static class Browser {
        final ShortenerService server;
        final Map<String, String> permanentRedirects = new HashMap<>();
        int serverHits;

        Browser(ShortenerService server) {
            this.server = server;
        }

        String click(String code) {
            String remembered = permanentRedirects.get(code);
            if (remembered != null) return remembered;
            serverHits++;
            Redirect redirect = server.resolve(code);
            if (redirect.status() == 301) permanentRedirects.put(code, redirect.location());
            return redirect.location();
        }
    }
}
