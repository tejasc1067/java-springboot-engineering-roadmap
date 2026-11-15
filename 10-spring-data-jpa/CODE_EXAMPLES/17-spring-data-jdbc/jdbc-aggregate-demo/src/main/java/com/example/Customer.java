package com.example;

import org.springframework.data.annotation.Id;

// Its own aggregate: saved, loaded and deleted through CustomerRepository, never through an Order.
public class Customer {
    @Id private Long id;
    private String name;

    public Customer(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
