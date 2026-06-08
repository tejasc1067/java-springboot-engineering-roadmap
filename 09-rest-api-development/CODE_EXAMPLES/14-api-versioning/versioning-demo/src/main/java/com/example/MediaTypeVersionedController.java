package com.example;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Strategy 3: media-type versioning. Same URL; client chooses via Accept header.
// REST-pure; awkward to test by hand.
@RestController
@RequestMapping("/api/media-books")
class MediaTypeVersionedController {

    @GetMapping(produces = "application/vnd.example.v1+json")
    public List<BookV1> v1() {
        return List.of(new BookV1(1L, "Effective Java by Bloch"));
    }

    @GetMapping(produces = "application/vnd.example.v2+json")
    public List<BookV2> v2() {
        return List.of(new BookV2(1L, "Effective Java", "Bloch"));
    }
}
