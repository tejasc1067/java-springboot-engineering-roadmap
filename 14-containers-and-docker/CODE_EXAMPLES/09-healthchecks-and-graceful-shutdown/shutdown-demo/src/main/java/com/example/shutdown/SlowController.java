package com.example.shutdown;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlowController {

    // Stands in for a request that takes a while: a report, a payment call, a big upload.
    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(3_000);
        return "finished";
    }
}
