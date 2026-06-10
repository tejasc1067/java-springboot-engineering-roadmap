package com.example;

// Any field may be null — null means "don't filter on this".
public record BookSearchCriteria(String author, String titleFragment, Integer minYear, Integer maxYear) {}
