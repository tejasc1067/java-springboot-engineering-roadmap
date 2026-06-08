package com.example;

import java.util.List;

// Wire shape matching Spring Data's Page<T>. Adopting it now means no JSON
// contract change when module 10 replaces this with the real thing.
public record Page<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> Page<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<T> slice = all.subList(from, to);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new Page<>(slice, page, size, all.size(), totalPages, page == 0, to >= all.size());
    }
}
