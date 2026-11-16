package com.example.lb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sticky sessions: the balancer sets a cookie naming the instance that served a user's first request
 * and sends every later request with that cookie back to the same instance. Each instance keeps its
 * sessions in its own memory (the setup topic 03 warns about).
 */
public final class StickySessions {

    private final List<String> alive = new ArrayList<>();
    private final Map<String, String> cookie = new HashMap<>();             // user -> instance
    private final Map<String, Integer> requestsPerInstance = new LinkedHashMap<>();
    private long nextNewUser;
    private int sessionsLost;

    public StickySessions(String... instances) {
        for (String instance : instances) addInstance(instance);
    }

    public void addInstance(String instance) {
        alive.add(instance);
        requestsPerInstance.put(instance, 0);
    }

    /** The instance's memory, and every session in it, is gone. */
    public void kill(String instance) {
        alive.remove(instance);
    }

    public String handle(String user) {
        String pinned = cookie.get(user);
        String instance;
        if (pinned != null && alive.contains(pinned)) {
            instance = pinned;
        } else {
            if (pinned != null) sessionsLost++;   // had a session, its instance is gone: logged out
            instance = alive.get((int) (nextNewUser++ % alive.size()));
            cookie.put(user, instance);
        }
        requestsPerInstance.merge(instance, 1, Integer::sum);
        return instance;
    }

    public Map<String, Integer> requestsPerInstance() { return requestsPerInstance; }

    public int sessionsLost() { return sessionsLost; }

    public void resetCounts() { requestsPerInstance.replaceAll((k, v) -> 0); }
}
