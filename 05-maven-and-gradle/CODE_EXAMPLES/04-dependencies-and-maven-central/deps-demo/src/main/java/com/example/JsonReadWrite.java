package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class JsonReadWrite {
    private static final Logger log = LoggerFactory.getLogger(JsonReadWrite.class);

    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(Map.of("user", "alice", "age", 30));
        log.info("serialized: {}", json);

        Map<?, ?> parsed = mapper.readValue(json, Map.class);
        log.info("parsed: {}", parsed);
    }
}
