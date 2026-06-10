package com.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(name = "page_count")
    private Integer pageCount;

    protected Book() {}

    public Book(String title, String author, Integer publishedYear, Integer pageCount) {
        this.title = title;
        this.author = author;
        this.publishedYear = publishedYear;
        this.pageCount = pageCount;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public Integer getPublishedYear() { return publishedYear; }
    public Integer getPageCount() { return pageCount; }

    public void setTitle(String title) { this.title = title; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
}
