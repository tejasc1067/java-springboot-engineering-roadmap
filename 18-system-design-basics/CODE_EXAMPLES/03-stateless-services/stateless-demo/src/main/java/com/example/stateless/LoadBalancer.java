package com.example.stateless;

import java.util.ArrayList;
import java.util.List;

/** Picks an instance per request. Module 18 topic 04 covers the algorithms; two are enough here. */
public class LoadBalancer<T> {

    private final List<T> instances;
    private final boolean sticky;
    private int next;

    private LoadBalancer(List<T> instances, boolean sticky) {
        this.instances = new ArrayList<>(instances);
        this.sticky = sticky;
    }

    public static <T> LoadBalancer<T> roundRobin(List<T> instances) {
        return new LoadBalancer<>(instances, false);
    }

    /** Sticky sessions: the same session id always lands on the same instance (while the list doesn't change). */
    public static <T> LoadBalancer<T> sticky(List<T> instances) {
        return new LoadBalancer<>(instances, true);
    }

    public T pick(String sessionId) {
        if (sticky) return instances.get(Math.floorMod(sessionId.hashCode(), instances.size()));
        return instances.get(next++ % instances.size());
    }

    /** An instance goes away: a deploy, a crash, a scale-down. */
    public void remove(T instance) {
        instances.remove(instance);
    }

    public void add(T instance) {
        instances.add(instance);
    }
}
