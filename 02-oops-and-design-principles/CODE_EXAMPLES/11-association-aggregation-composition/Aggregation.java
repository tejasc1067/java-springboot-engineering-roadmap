// Aggregation: the Library has Books, but the Books exist independently —
// they were created outside the Library and could be moved elsewhere.

import java.util.ArrayList;
import java.util.List;

class Book {
    String title;
    Book(String title) { this.title = title; }
    @Override public String toString() { return "Book[" + title + "]"; }
}

class Library {
    String name;
    List<Book> books;

    Library(String name, List<Book> books) {
        this.name = name;
        this.books = books;          // accepts books that already exist elsewhere
    }
}

public class Aggregation {
    public static void main(String[] args) {
        // Books exist on their own.
        Book b1 = new Book("Dune");
        Book b2 = new Book("Foundation");
        Book b3 = new Book("Neuromancer");

        // Two libraries can share or transfer books.
        Library central = new Library("Central", new ArrayList<>(List.of(b1, b2, b3)));
        Library branch  = new Library("Branch",  new ArrayList<>());

        // Move a book between libraries — both books and libraries persist.
        central.books.remove(b3);
        branch.books.add(b3);

        System.out.println(central.name + ": " + central.books);
        System.out.println(branch.name  + ": " + branch.books);

        // The Book's existence is not tied to either Library.
    }
}
