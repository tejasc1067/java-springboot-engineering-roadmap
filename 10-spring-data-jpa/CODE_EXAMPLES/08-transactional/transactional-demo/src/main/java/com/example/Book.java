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

    @Column(name = "page_count")
    private Integer pageCount;

    protected Book() {}

    public Book(String title, Integer pageCount) {
        this.title = title;
        this.pageCount = pageCount;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Integer getPageCount() { return pageCount; }

    public void setTitle(String title) { this.title = title; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
}
