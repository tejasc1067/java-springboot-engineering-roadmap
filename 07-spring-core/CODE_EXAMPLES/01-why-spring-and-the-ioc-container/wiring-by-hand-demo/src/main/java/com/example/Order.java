package com.example;

import java.math.BigDecimal;
import java.util.List;

// Value object. NOT a bean. You build one with `new` whenever you need one,
// the same way you would a String. The container manages services, not values.
public record Order(String id, String customer, List<String> items, BigDecimal total) {
}
