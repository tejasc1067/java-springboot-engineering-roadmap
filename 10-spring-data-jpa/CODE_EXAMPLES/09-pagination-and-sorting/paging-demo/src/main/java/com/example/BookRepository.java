package com.example;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Page<T> returns content + total count + total pages. Costs 2 queries (1 SELECT, 1 COUNT).
    Page<Book> findByAuthor(String author, Pageable pageable);

    // Slice<T> returns content + "is there a next page?" — only 1 query (asks for size+1 rows).
    Slice<Book> findSliceByAuthor(String author, Pageable pageable);
}
