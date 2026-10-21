package com.fdl.platform.order.domain;

/** Item de um pedido a ser criado, antes de receber identificador e posição. */
public record NewItem(String name, int quantity, long unitPriceCents) {

    public NewItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O item precisa de nome");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("A quantidade do item precisa ser positiva");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("O preço unitário não pode ser negativo");
        }
    }
}
