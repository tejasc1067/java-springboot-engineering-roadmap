package com.example;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Strategy 1: URI versioning. Version lives in the path. Easiest to debug and
// the default choice for most new services. In real code each version would be
// its own controller class (BookV1Controller, BookV2Controller); we colocate
// here purely for the side-by-side demo.
@RestController
class UriVersionedController {

    @GetMapping("/api/v1/books")
    public List<BookV1> v1() {
        return List.of(new BookV1(1L, "Effective Java by Bloch"));
    }

    @GetMapping("/api/v2/books")
    public List<BookV2> v2() {
        return List.of(new BookV2(1L, "Effective Java", "Bloch"));
    }
}
