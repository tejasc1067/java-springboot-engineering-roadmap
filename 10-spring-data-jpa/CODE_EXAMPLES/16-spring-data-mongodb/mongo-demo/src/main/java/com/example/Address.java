package com.example;

// Embedded on purpose: an order's shipping address should stay as it was when the order was placed,
// even if the customer moves later. A stale copy is the correct behaviour here.
public record Address(String city, String postcode) { }
