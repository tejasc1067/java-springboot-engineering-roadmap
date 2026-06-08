package com.example;

import java.util.List;

public record Book(Long id, String title, String author, List<String> tags) {}
