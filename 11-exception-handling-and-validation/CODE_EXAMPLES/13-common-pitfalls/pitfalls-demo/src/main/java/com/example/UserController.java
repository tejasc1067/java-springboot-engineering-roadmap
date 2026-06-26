package com.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// @Validated at the class level is what makes the @Min on the path variable run.
// Without it, the GET endpoint's constraint is ignored (the @Valid-style body case
// works without @Validated, but parameter constraints do not).
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    // PITFALL 1 — the constraints on CreateUserRequest exist, but no @Valid means
    // they never run. A blank username sails through and returns 201.
    @PostMapping("/without-valid")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserRequest createWithoutValid(@RequestBody CreateUserRequest req) {
        return req;
    }

    // FIX — @Valid runs the constraints before the body executes. Blank input -> 400,
    // handled by GlobalExceptionHandler.handleMethodArgumentNotValid (MethodArgumentNotValidException).
    @PostMapping("/with-valid")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserRequest createWithValid(@Valid @RequestBody CreateUserRequest req) {
        return req;
    }

    // Parameter validation. A bad id throws jakarta.validation.ConstraintViolationException
    // — a DIFFERENT exception from the body case above (pitfall 6). The advice handles both.
    @GetMapping("/{id}")
    public String one(@PathVariable @Min(1) Long id) {
        return "user " + id;
    }
}
