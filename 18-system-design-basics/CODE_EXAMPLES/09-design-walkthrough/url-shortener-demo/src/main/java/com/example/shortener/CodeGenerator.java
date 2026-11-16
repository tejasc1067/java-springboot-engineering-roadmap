package com.example.shortener;

@FunctionalInterface
public interface CodeGenerator {
    String generate(String longUrl);
}
