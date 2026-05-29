package com.example;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class LifecycleResource {

    private boolean open;

    @PostConstruct
    public void open() {
        this.open = true;
        System.out.println("LifecycleResource: opened");
    }

    @PreDestroy
    public void close() {
        this.open = false;
        System.out.println("LifecycleResource: closed");
    }

    public boolean isOpen() {
        return open;
    }
}
