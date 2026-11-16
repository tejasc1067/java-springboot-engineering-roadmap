package com.example.products;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Product {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private Integer stock;   // null = never counted

    protected Product() { }

    public Product(String name, Integer stock) {
        this.name = name;
        this.stock = stock;
    }

    public String getName() { return name; }
}
