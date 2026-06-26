package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Throws the SAME exception as BookController, but declares no @ExceptionHandler.
// The handler in BookController does not apply here — that is the scope limit this topic demonstrates.
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @GetMapping("/{id}")
    public String forBook(@PathVariable Long id) {
        // Pretend we looked up the book to build a report and it wasn't there.
        throw new BookNotFoundException(id);   // no local handler -> falls through to 500
    }
}
