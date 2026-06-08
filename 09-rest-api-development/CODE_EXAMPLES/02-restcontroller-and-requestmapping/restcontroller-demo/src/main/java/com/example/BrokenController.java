package com.example;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// @Controller WITHOUT @ResponseBody. Spring treats the return value as a view name
// and tries to render it. With no template engine on the classpath, Spring falls
// back to the Whitelabel error page and returns 500.
//
// This is the failure mode of using plain @Controller for a JSON API.
@Controller
@RequestMapping("/broken")
public class BrokenController {

    @GetMapping("/books")
    public List<Book> all() {
        return List.of(new Book(1L, "Effective Java", "Bloch"));
    }
}
