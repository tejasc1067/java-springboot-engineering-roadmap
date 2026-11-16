package com.example.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CardLogging {

    private static final Logger log = LoggerFactory.getLogger(CardLogging.class);

    /** Broken: a record's generated toString prints every field, card number included. */
    public record RawCard(String holder, String number) { }

    /** Fixed: toString shows only what an operator needs. */
    public record Card(String holder, String number) {
        @Override
        public String toString() {
            return "Card[holder=" + holder + ", number=****" + number.substring(number.length() - 4) + "]";
        }
    }

    public void chargeBroken(RawCard card) {
        log.info("charging {}", card);
    }

    public void charge(Card card) {
        log.info("charging {}", card);
    }
}
