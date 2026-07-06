package com.example;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// The bridge between "how Spring Security asks for a user" and "where our users actually live".
// Spring Security calls loadUserByUsername during authentication; we look the user up in the database and
// return a UserDetails carrying the STORED (already-hashed) password plus the user's authorities. Spring
// Security then compares the submitted password to that hash itself — this class never sees the raw one.
//
// Because this bean exists, Spring Boot stops creating its default in-memory "user" (topic 02): our
// database is now the single source of truth for who can log in.
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public JpaUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("no user: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())   // the BCrypt hash from the DB, NOT a raw password
                .authorities(user.getRole())     // e.g. "ROLE_USER"
                .build();
    }
}
