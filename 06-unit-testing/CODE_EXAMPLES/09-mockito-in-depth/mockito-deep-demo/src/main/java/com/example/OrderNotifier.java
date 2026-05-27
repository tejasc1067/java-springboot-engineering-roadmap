package com.example;

import java.util.List;

public class OrderNotifier {

    private final MessageGateway gateway;

    public OrderNotifier(MessageGateway gateway) {
        this.gateway = gateway;
    }

    /** Builds a confirmation message and sends it. */
    public void confirm(String email, String orderId) {
        gateway.send(email, "Order " + orderId + " confirmed");
    }

    /** Sends the same message to everyone; returns how many were accepted. */
    public int broadcast(List<String> emails, String message) {
        int delivered = 0;
        for (String email : emails) {
            if (gateway.send(email, message)) {
                delivered++;
            }
        }
        return delivered;
    }
}
