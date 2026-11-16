package com.example.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.system.CapturedOutput;

import java.util.List;

/** Each line of structured output is one JSON object: parse it like a log platform would. */
final class JsonLines {

    private static final ObjectMapper JSON = new ObjectMapper();

    static List<JsonNode> parse(CapturedOutput output) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(JsonLines::read)
                .toList();
    }

    static JsonNode withMessage(CapturedOutput output, String message) {
        return parse(output).stream()
                .filter(event -> event.path("message").asText().equals(message))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no log event with message: " + message));
    }

    private static JsonNode read(String line) {
        try {
            return JSON.readTree(line);
        } catch (Exception e) {
            throw new AssertionError("not JSON: " + line, e);
        }
    }

    private JsonLines() { }
}
