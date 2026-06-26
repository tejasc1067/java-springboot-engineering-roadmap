package com.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();

    public BookController() {
        store.put(1L, new Book(1L, "The Pragmatic Programmer", "Hunt & Thomas", "9780201616224"));
    }

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        Book book = store.get(id);
        if (book == null) {
            throw new BookNotFoundException(id);
        }
        return book;
    }

    // Handles BookNotFoundException thrown by THIS controller only. Returning a ProblemDetail
    // sets Content-Type: application/problem+json and serializes the status/title/detail fields.
    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Book not found");
        return problem;
    }
}
