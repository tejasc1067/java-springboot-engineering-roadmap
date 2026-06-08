package com.example;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Strategy 2: header versioning. Same URL, different X-API-Version header.
// Spring routes on header values via the headers attribute.
@RestController
@RequestMapping("/api/header-books")
class HeaderVersionedController {

    @GetMapping(headers = "X-API-Version=1")
    public List<BookV1> v1() {
        return List.of(new BookV1(1L, "Effective Java by Bloch"));
    }

    @GetMapping(headers = "X-API-Version=2")
    public List<BookV2> v2() {
        return List.of(new BookV2(1L, "Effective Java", "Bloch"));
    }
}
