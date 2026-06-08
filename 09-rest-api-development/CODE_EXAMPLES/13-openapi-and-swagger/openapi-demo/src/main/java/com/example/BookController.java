package com.example;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Books", description = "Catalog operations")
@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final List<Book> CATALOG = List.of(
            new Book(1L, "Effective Java", "Bloch"),
            new Book(2L, "Clean Code", "Martin"));

    @Operation(summary = "List all books")
    @GetMapping
    public List<Book> all() {
        return CATALOG;
    }

    @Operation(summary = "Find one book by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book returned"),
            @ApiResponse(responseCode = "404", description = "No such book")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Book> one(
            @Parameter(description = "Book id", example = "1")
            @PathVariable Long id) {
        return CATALOG.stream()
                .filter(b -> b.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
