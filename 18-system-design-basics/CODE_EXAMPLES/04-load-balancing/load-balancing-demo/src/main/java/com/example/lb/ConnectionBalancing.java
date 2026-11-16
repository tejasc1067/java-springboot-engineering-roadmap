package com.example.lb;

/**
 * Clients that keep one connection open and send many requests over it (HTTP keep-alive, gRPC,
 * WebSocket). A layer-4 balancer chooses an instance once per connection; a layer-7 balancer chooses
 * once per request.
 */
public final class ConnectionBalancing {

    /**
     * {@code clients} clients each send {@code requestsPerClient} requests. After the first
     * {@code scaleOutAfter} requests of every client, the pool grows from {@code before} to {@code after}
     * instances. A client reconnects after {@code maxRequestsPerConnection} requests (0 = never).
     *
     * @return requests handled by each instance after the scale-out
     */
    public static long[] layer4(int clients, int requestsPerClient, int scaleOutAfter,
                                int before, int after, int maxRequestsPerConnection) {
        long[] handled = new long[after];
        int[] connection = new int[clients];
        int[] requestsOnConnection = new int[clients];
        long nextConnection = 0;

        for (int c = 0; c < clients; c++) connection[c] = (int) (nextConnection++ % before);

        for (int r = 0; r < requestsPerClient; r++) {
            int pool = r < scaleOutAfter ? before : after;
            for (int c = 0; c < clients; c++) {
                if (maxRequestsPerConnection > 0 && requestsOnConnection[c] == maxRequestsPerConnection) {
                    connection[c] = (int) (nextConnection++ % pool);   // new connection, balanced again
                    requestsOnConnection[c] = 0;
                }
                requestsOnConnection[c]++;
                if (r >= scaleOutAfter) handled[connection[c]]++;
            }
        }
        return handled;
    }

    /** Layer 7 picks per request (round-robin here), so the new instances get work immediately. */
    public static long[] layer7(int clients, int requestsPerClient, int scaleOutAfter, int after) {
        long[] handled = new long[after];
        long next = 0;
        for (int r = scaleOutAfter; r < requestsPerClient; r++) {
            for (int c = 0; c < clients; c++) handled[(int) (next++ % after)]++;
        }
        return handled;
    }
}
