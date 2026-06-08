package com.example;

// v2 contract: "name" split into title + author. Breaking change vs v1.
public record BookV2(Long id, String title, String author) {}
