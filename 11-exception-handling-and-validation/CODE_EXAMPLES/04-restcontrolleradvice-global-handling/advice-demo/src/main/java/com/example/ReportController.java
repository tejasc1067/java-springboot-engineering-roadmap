package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// In topic 03 this controller's BookNotFoundException fell through to 500, because the
// handler was local to BookController. Now the global advice covers it too — same 404, no code here.
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @GetMapping("/{id}")
    public String forBook(@PathVariable Long id) {
        throw new BookNotFoundException(id);
    }
}
