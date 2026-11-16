package com.example.sharding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class Keys {

    private Keys() { }

    static List<String> users(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) keys.add("user-" + i);
        return keys;
    }

    /** Standard deviation of the per-node key counts, as a percentage of the average count. */
    static double spreadPercent(Collection<Integer> loads) {
        double mean = loads.stream().mapToInt(Integer::intValue).average().orElseThrow();
        double variance = loads.stream().mapToDouble(l -> (l - mean) * (l - mean)).average().orElseThrow();
        return 100 * Math.sqrt(variance) / mean;
    }
}
