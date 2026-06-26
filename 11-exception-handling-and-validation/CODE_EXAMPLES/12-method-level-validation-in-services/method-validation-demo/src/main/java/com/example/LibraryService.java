package com.example;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

// @Validated (Spring's, NOT @Valid) makes Spring's MethodValidationPostProcessor wrap this
// bean in a proxy that checks parameter constraints on every public-method call. Without it,
// the @Min / @Positive annotations below are inert — the method runs with whatever it's given.
@Service
@Validated
public class LibraryService {

    // bookId -> copies currently in stock. Seeded so the demo has something to borrow against.
    private final Map<Long, Integer> stock = new ConcurrentHashMap<>(Map.of(1L, 3, 2L, 0));

    // Two DIFFERENT kinds of check live in one method:
    //
    //   1. INPUT VALIDATION (the annotations). @Min(1)/@Positive guard the SHAPE of the args.
    //      A violation throws ConstraintViolationException BEFORE the body runs. This is a
    //      defensive guard: the controller should have already validated user input, so a
    //      violation here means a CALLER passed bad args — a programming error, not user error.
    //
    //   2. BUSINESS-RULE ENFORCEMENT (the if-block). "Is there enough stock?" needs runtime
    //      data no annotation can see. It throws a domain exception -> 409.
    public int borrow(@Min(1) Long bookId, @Positive int copies) {
        int available = stock.getOrDefault(bookId, 0);
        if (copies > available) {
            throw new InsufficientCopiesException(bookId, copies, available);
        }
        int remaining = available - copies;
        stock.put(bookId, remaining);
        return remaining;
    }
}
