package com.example;

public class FancyGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "*** GREETINGS, " + name.toUpperCase() + "! ***";
    }
}
