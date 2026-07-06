package com.example;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Spring Security identifies a user by username, so this is the lookup the UserDetailsService needs.
    Optional<AppUser> findByUsername(String username);
}
