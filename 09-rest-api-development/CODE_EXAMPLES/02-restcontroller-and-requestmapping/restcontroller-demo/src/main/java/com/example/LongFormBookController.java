package com.example;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

// Equivalent to BookController, written the long way.
// @ResponseBody on every method is what @RestController collapses for you.
@Controller
@RequestMapping("/legacy/books")
public class LongFormBookController {

    @GetMapping
    @ResponseBody
    public List<Book> all() {
        return List.of(new Book(1L, "Effective Java", "Bloch"));
    }
}
