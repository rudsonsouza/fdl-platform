package com.fdl.platform.order.api;

import com.fdl.platform.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Linha da listagem: sem itens, endereço nem histórico. */
public record OrderSummaryResponse(UUID id, String customerName, OrderStatus status, long totalCents,
        Instant createdAt, Instant updatedAt) {
}
