package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RelationshipTest {

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void cascadeFromAuthorPersistsBooks() {
        Author bloch = new Author("Joshua Bloch", "USA");
        bloch.addBook(new Book("Effective Java", 2017));
        bloch.addBook(new Book("Java Puzzlers", 2005));

        authorRepository.save(bloch);   // cascade = ALL means the books get persisted too

        assertThat(authorRepository.count()).isEqualTo(1);
        assertThat(bookRepository.count()).isEqualTo(2);
    }

    @Test
    void bookOwnsTheForeignKey() {
        Author martin = new Author("Robert Martin", "USA");
        Book cleanCode = new Book("Clean Code", 2008);
        martin.addBook(cleanCode);
        authorRepository.save(martin);

        Book reloaded = bookRepository.findById(cleanCode.getId()).orElseThrow();
        assertThat(reloaded.getAuthor()).isNotNull();
        assertThat(reloaded.getAuthor().getName()).isEqualTo("Robert Martin");
    }

    @Test
    void traverseRelationshipViaDerivedMethod() {
        Author bloch = new Author("Joshua Bloch", "USA");
        bloch.addBook(new Book("Effective Java", 2017));
        bloch.addBook(new Book("Java Puzzlers", 2005));
        authorRepository.save(bloch);

        Author fowler = new Author("Martin Fowler", "UK");
        fowler.addBook(new Book("Refactoring", 2018));
        authorRepository.save(fowler);

        List<Book> blochBooks = bookRepository.findByAuthor_Name("Joshua Bloch");
        List<Book> usaBooks = bookRepository.findByAuthor_Country("USA");

        assertThat(blochBooks).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Java Puzzlers");
        assertThat(usaBooks).hasSize(2);
    }

    @Test
    void orphanRemovalDeletesUnreferencedBook() {
        Author bloch = new Author("Joshua Bloch", "USA");
        Book first = new Book("Effective Java", 2017);
        Book second = new Book("Java Puzzlers", 2005);
        bloch.addBook(first);
        bloch.addBook(second);
        authorRepository.save(bloch);

        bloch.removeBook(second);
        authorRepository.save(bloch);

        assertThat(bookRepository.count()).isEqualTo(1);
        assertThat(bookRepository.findById(second.getId())).isEmpty();
    }

    @Test
    void deletingAuthorCascadesToBooks() {
        Author bloch = new Author("Joshua Bloch", "USA");
        bloch.addBook(new Book("Effective Java", 2017));
        authorRepository.save(bloch);

        authorRepository.delete(bloch);

        assertThat(authorRepository.count()).isZero();
        assertThat(bookRepository.count()).isZero();
    }
}
