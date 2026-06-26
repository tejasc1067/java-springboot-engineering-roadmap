package com.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// @Validated (Spring's, NOT @Valid) on the CLASS is what makes constraints on @PathVariable /
// @RequestParam actually run. Without it, the @Min/@Max below are ignored and bad params sail through.
// A param-constraint failure throws jakarta.validation.ConstraintViolationException — a DIFFERENT
// exception from the @Valid @RequestBody case (MethodArgumentNotValidException). The advice unifies both.
@RestController
@RequestMapping("/api/books")
@Validated
public class BookController {

    @GetMapping("/{id}")
    public String one(@PathVariable @Min(1) Long id) {
        return "book " + id;
    }

    @GetMapping
    public String page(@RequestParam @Min(1) @Max(100) int size) {
        return "page of " + size;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody CreateBookRequest request) {
        return "created " + request.title();
    }
}
