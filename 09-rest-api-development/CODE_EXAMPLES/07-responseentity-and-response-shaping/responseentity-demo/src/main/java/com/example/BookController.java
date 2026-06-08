package com.example;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    // List endpoint: always 200. No need to wrap in ResponseEntity.
    @GetMapping
    public Collection<Book> all() {
        return store.values();
    }

    // GET one: 200 with body, or 404 with no body. Wrap in ResponseEntity.
    @GetMapping("/{id}")
    public ResponseEntity<Book> one(@PathVariable Long id) {
        return Optional.ofNullable(store.get(id))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST: 201 Created plus a Location header pointing at the new resource.
    // ServletUriComponentsBuilder builds the URI relative to whatever URL the
    // client used -- works behind reverse proxies, port-mappings, etc.
    @PostMapping
    public ResponseEntity<Book> create(@RequestBody Book incoming) {
        long id = nextId.getAndIncrement();
        Book created = new Book(id, incoming.title(), incoming.author());
        store.put(id, created);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // DELETE: 204 No Content on success, 404 if the id is missing.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (store.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
