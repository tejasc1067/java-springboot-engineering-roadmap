package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A toy document database: one collection of JSON documents, looked up by id.
 * It behaves like MongoDB's simplest case (no secondary indexes, no multi-document transactions),
 * so each test can show a trade-off without running a real server. Topic 16 uses real MongoDB.
 */
public class DocumentStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, String> documents = new LinkedHashMap<>(); // id -> JSON text
    private int lookups = 0;
    private int documentsScanned = 0;

    /** Stores any JSON object. No schema: whatever fields the document has are accepted. */
    public void save(String id, ObjectNode document) {
        documents.put(id, document.toString());
    }

    public ObjectNode findById(String id) {
        lookups++;
        String text = documents.get(id);
        return text == null ? null : parse(text);
    }

    /** Without an index on the field, the only way to answer "which documents match?" is to read them all. */
    public List<ObjectNode> findWhere(Predicate<ObjectNode> filter) {
        List<ObjectNode> matches = new ArrayList<>();
        for (String text : documents.values()) {
            documentsScanned++;
            ObjectNode doc = parse(text);
            if (filter.test(doc)) matches.add(doc);
        }
        return matches;
    }

    public int lookups() { return lookups; }
    public int documentsScanned() { return documentsScanned; }

    public static ObjectNode newDocument() { return JSON.createObjectNode(); }

    private static ObjectNode parse(String text) {
        try {
            return (ObjectNode) JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
