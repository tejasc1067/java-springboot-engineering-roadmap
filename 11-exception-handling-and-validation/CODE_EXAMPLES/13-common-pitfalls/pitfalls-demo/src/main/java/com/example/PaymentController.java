package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    // PITFALL 3 — the catch swallows the failure: it returns 200 with a cheerful body
    // and logs nothing. The charge failed, the client is told "ok", and there is no log
    // line to find later. An invisible outage.
    @GetMapping("/swallow")
    public String charge() {
        try {
            return chargeCard();
        } catch (Exception ex) {
            return "ok";   // silent failure, no status change, no log
        }
    }

    // FIX — don't catch what you can't handle. Let it propagate to the advice, which
    // maps it to a real 500 and logs the cause. (If you must catch here, log AND re-throw.)
    @GetMapping("/propagate")
    public String chargeProperly() {
        return chargeCard();
    }

    private String chargeCard() {
        log.info("attempting charge");
        throw new IllegalStateException("payment gateway timed out");
    }
}
