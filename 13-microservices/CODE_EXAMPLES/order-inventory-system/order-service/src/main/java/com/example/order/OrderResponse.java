package com.example.order;

public record OrderResponse(Long id, String sku, int quantity, String status) {

    static OrderResponse from(OrderRecord order) {
        return new OrderResponse(order.getId(), order.getSku(), order.getQuantity(),
                order.getStatus().name());
    }
}
