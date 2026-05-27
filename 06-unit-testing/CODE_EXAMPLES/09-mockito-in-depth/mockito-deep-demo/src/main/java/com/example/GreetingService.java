package com.example;

/**
 * A real class with two methods, one calling the other. Used to show a spy:
 * we keep greet() running for real but override prefix().
 */
public class GreetingService {

    public String greet(String name) {
        return prefix() + name;
    }

    String prefix() {
        return "Hello, ";
    }
}
