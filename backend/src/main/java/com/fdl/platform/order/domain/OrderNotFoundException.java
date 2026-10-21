package com.fdl.platform.order.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Pedido %s não encontrado".formatted(id));
    }
}
