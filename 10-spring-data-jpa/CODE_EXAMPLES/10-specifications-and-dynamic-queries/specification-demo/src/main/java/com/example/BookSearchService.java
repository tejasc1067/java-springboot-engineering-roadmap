package com.example;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookSearchService {

    private final BookRepository repository;

    public BookSearchService(BookRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Book> search(BookSearchCriteria criteria) {
        return repository.findAll(buildSpec(criteria));
    }

    @Transactional(readOnly = true)
    public Page<Book> search(BookSearchCriteria criteria, Pageable pageable) {
        return repository.findAll(buildSpec(criteria), pageable);
    }

    // Compose the Specification by adding only the filters whose criteria field is non-null.
    private Specification<Book> buildSpec(BookSearchCriteria c) {
        Specification<Book> spec = Specification.unrestricted();

        if (c.author() != null) {
            spec = spec.and(BookSpecs.hasAuthor(c.author()));
        }
        if (c.titleFragment() != null) {
            spec = spec.and(BookSpecs.titleContains(c.titleFragment()));
        }
        if (c.minYear() != null) {
            spec = spec.and(BookSpecs.publishedYearAtLeast(c.minYear()));
        }
        if (c.maxYear() != null) {
            spec = spec.and(BookSpecs.publishedYearAtMost(c.maxYear()));
        }
        return spec;
    }
}
