package com.example.compose;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notes")
public class NoteController {

    private final NoteRepository notes;

    public NoteController(NoteRepository notes) { this.notes = notes; }

    @PostMapping
    public Note add(@RequestBody String text) { return notes.save(new Note(text)); }

    @GetMapping
    public List<Note> all() { return notes.findAll(); }
}
