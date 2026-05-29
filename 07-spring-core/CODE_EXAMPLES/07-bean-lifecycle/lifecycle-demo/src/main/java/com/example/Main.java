package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        System.out.println("-- about to build the context --");
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("-- context is ready --");

            WarmedCache cache = context.getBean(WarmedCache.class);
            LifecycleResource resource = context.getBean(LifecycleResource.class);

            System.out.println("cache.get(ABC) = " + cache.get("ABC"));
            System.out.println("resource.isOpen() = " + resource.isOpen());

            System.out.println("-- leaving try-with-resources (context will close) --");
        }
        System.out.println("-- context closed --");
    }
}
