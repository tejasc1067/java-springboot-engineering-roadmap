package com.example;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    // Dirty checking: no save() call needed inside @Transactional.
    @Transactional
    public void renameBook(Long id, String newTitle) {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);
    }

    // Rolls back on RuntimeException by default.
    @Transactional
    public void renameAndThrowRuntime(Long id, String newTitle) {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);
        throw new RuntimeException("boom");
    }

    // Does NOT roll back on a checked exception by default — this is the classic gotcha.
    @Transactional
    public void renameAndThrowChecked(Long id, String newTitle) throws OutOfPagesException {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);
        throw new OutOfPagesException("checked");
    }

    // Tell Spring to also roll back on checked exceptions.
    @Transactional(rollbackFor = OutOfPagesException.class)
    public void renameAndThrowChecked_withRollbackFor(Long id, String newTitle) throws OutOfPagesException {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);
        throw new OutOfPagesException("checked-with-rollbackFor");
    }

    // readOnly = true: optimization hint and a guard against accidental writes.
    @Transactional(readOnly = true)
    public Book findById(Long id) {
        return repository.findById(id).orElseThrow();
    }

    // Tries to modify a managed entity inside a readOnly transaction.
    // Hibernate disables dirty checking in readOnly sessions, so the change is not flushed.
    @Transactional(readOnly = true)
    public void renameInReadOnly(Long id, String newTitle) {
        Book book = repository.findById(id).orElseThrow();
        book.setTitle(newTitle);
    }

    // Self-invocation pitfall: calling 'this.renameBook(...)' bypasses the AOP proxy,
    // so renameBook's @Transactional is NOT applied when reached via this path.
    public void renameViaSelfInvocation(Long id, String newTitle) {
        this.renameBook(id, newTitle);  // no transaction is opened for the inner call
    }
}
