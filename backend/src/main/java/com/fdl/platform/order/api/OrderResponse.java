package com.fdl.platform.order.api;

import com.fdl.platform.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido completo. {@code allowedTransitions} é vazio nos estados finais. */
public record OrderResponse(
        UUID id,
        Customer customer,
        List<Item> items,
        Address deliveryAddress,
        OrderStatus status,
        long totalCents,
        List<OrderStatus> allowedTransitions,
        List<HistoryEntry> history,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public record Customer(String name, String phone) {
    }

    public record Item(UUID id, String name, int quantity, long unitPriceCents, long subtotalCents) {
    }

    public record Address(String street, String number, String complement, String neighborhood, String city,
            String state, String zipCode) {
    }

    /** {@code from} é nulo na entrada de criação. */
    public record HistoryEntry(OrderStatus from, OrderStatus to, UUID changedBy, Instant changedAt) {
    }
}
