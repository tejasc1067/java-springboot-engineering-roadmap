package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println(context.getBean(ConstructorGreeter.class).greet("ada"));
            System.out.println(context.getBean(SetterGreeter.class).greet("ada"));
            System.out.println(context.getBean(FieldGreeter.class).greet("ada"));
        }
        // All three print "HELLO, ADA". The runtime behavior is identical.
        // The differences are in how testable and how mistake-resistant each one is --
        // see the test classes.
    }
}
