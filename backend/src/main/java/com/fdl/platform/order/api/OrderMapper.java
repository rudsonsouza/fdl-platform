package com.fdl.platform.order.api;

import com.fdl.platform.order.domain.DeliveryAddress;
import com.fdl.platform.order.domain.NewItem;
import com.fdl.platform.order.domain.Order;
import java.util.List;

/** Conversões entre os DTOs da API e o domínio. Precisa rodar dentro da transação (coleções preguiçosas). */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static DeliveryAddress toDomain(AddressRequest a) {
        return new DeliveryAddress(a.street().strip(), a.number().strip(), blankToNull(a.complement()),
                a.neighborhood().strip(), a.city().strip(), a.state(), a.zipCode());
    }

    public static List<NewItem> toDomain(List<OrderItemRequest> items) {
        return items.stream()
                .map(i -> new NewItem(i.name().strip(), i.quantity(), i.unitPriceCents()))
                .toList();
    }

    public static OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getCustomerName(), o.getStatus(), o.getTotalItems(),
                o.getTotalCents(), o.getCreatedAt(), o.getUpdatedAt());
    }

    public static OrderResponse toResponse(Order o) {
        var a = o.getDeliveryAddress();
        return new OrderResponse(
                o.getId(),
                new OrderResponse.Customer(o.getCustomerName(), o.getCustomerPhone()),
                o.getItems().stream()
                        .map(i -> new OrderResponse.Item(i.getId(), i.getName(), i.getQuantity(),
                                i.getUnitPriceCents(), i.getUnitPriceCents() * i.getQuantity()))
                        .toList(),
                new OrderResponse.Address(a.getStreet(), a.getNumber(), a.getComplement(), a.getNeighborhood(),
                        a.getCity(), a.getState(), a.getZipCode()),
                o.getStatus(),
                o.getTotalCents(),
                o.allowedTransitions(),
                o.getHistory().stream()
                        .map(h -> new OrderResponse.HistoryEntry(h.getFromStatus(), h.getToStatus(),
                                h.getChangedBy(), h.getChangedAt()))
                        .toList(),
                o.getCreatedBy(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
