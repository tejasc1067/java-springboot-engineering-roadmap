package com.example;

import java.util.Optional;

/**
 * In production this would hit a real database (the JDBC code from module 04).
 * In a unit test we don't want a database, so we replace it with a mock.
 */
public interface UserRepository {
    Optional<User> findById(long id);
}
