package com.example;

import org.springframework.stereotype.Service;

// Unaware of the aspect. The aspect's pointcut matches its methods anyway.
@Service
public class OrderService {

    public String place(String customer) {
        return "order-for-" + customer;
    }
}
