package com.example.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Spring Session's default Redis serializer is Java serialization, so session attributes must be Serializable. */
public class SerializableCart implements Serializable {
    private final ArrayList<String> items = new ArrayList<>();

    public void add(String item) {
        items.add(item);
    }

    public List<String> items() {
        return items;
    }
}
