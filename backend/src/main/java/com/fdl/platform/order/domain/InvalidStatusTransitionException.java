package com.fdl.platform.order.domain;

import java.util.List;

public class InvalidStatusTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidStatusTransitionException(OrderStatus currentStatus, OrderStatus requestedStatus) {
        super("Não é possível ir de %s para %s".formatted(currentStatus, requestedStatus));
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }

    public OrderStatus requestedStatus() {
        return requestedStatus;
    }

    public List<OrderStatus> allowedTransitions() {
        return currentStatus.allowedTransitions();
    }
}
