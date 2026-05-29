package com.example;

import java.math.BigDecimal;
import java.util.List;

public record Order(String id, String customer, List<String> items, BigDecimal total) {
}
