package com.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A user row in the database. This is OUR domain object — deliberately separate from Spring Security's
// UserDetails. JpaUserDetailsService translates one into the other, so our persistence model and the
// framework's authentication model can evolve independently.
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // Stores the BCrypt HASH, never the raw password. Topic 05 shows what goes wrong if you store plaintext.
    @Column(nullable = false)
    private String password;

    // One role per user keeps this topic on authentication; multiple roles/authorities is topic 07.
    @Column(nullable = false)
    private String role;

    protected AppUser() {   // JPA needs a no-arg constructor; protected discourages direct use
    }

    public AppUser(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }
}
