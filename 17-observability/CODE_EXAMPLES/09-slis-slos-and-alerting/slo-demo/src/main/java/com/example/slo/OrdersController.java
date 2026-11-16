package com.example.slo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrdersController {

    @GetMapping("/orders/{id}")
    public String order(@PathVariable long id) {
        if (id == 0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "database unavailable");
        if (id < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return "order " + id;
    }
}
