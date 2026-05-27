package com.example;

public interface MessageGateway {
    boolean send(String to, String message);
}
