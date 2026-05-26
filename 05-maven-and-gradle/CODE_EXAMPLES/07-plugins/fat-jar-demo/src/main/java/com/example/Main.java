package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        String json = new ObjectMapper().writeValueAsString(Map.of("status", "ok"));
        System.out.println(json);
    }
}
