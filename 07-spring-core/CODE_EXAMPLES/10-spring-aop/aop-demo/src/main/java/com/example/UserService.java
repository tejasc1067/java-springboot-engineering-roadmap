package com.example;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    public String lookup(String id) {
        return "user-" + id;
    }
}
