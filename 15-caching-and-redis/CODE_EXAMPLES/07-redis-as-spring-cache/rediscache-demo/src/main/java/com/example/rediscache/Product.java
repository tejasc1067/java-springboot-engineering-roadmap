package com.example.rediscache;

// Deliberately not Serializable: fine for JSON, fatal for Redis's default JDK serializer.
public record Product(long id, String name, long priceCents) { }
