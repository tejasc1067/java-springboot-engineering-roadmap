package com.example.app;

import com.example.core.Greeter;

public class App {
    public static void main(String[] args) {
        System.out.println(new Greeter().greet("multi-module Maven"));
    }
}
