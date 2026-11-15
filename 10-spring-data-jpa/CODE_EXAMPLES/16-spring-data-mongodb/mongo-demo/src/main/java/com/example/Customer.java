package com.example;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("customers")
public class Customer {

    @Id
    private String id;

    // Declares a unique index. Spring Data does NOT create it unless
    // spring.data.mongodb.auto-index-creation=true - see UniqueIndexTest.
    @Indexed(unique = true)
    private String email;

    private String name;

    public Customer() { }

    public Customer(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
}
