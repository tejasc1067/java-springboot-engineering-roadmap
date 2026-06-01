package com.example;

public class PlainGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name + ".";
    }
}
