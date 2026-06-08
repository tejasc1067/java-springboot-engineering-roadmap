package com.example;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Clock clock = Clock.systemUTC();

    public User create(String username, String password) {
        long id = nextId.getAndIncrement();
        // Real apps use BCrypt/Argon2. This is a toy hash for the demo.
        String hash = "hashed::" + Integer.toHexString(password.hashCode());
        User user = new User(id, username, hash, clock.instant());
        store.put(id, user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}
