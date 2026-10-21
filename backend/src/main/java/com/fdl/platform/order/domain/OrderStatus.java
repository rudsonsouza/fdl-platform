package com.fdl.platform.order.domain;

import java.util.List;

/**
 * Estados de um pedido e a máquina de transições: fluxo linear, cancelamento só antes da saída
 * para entrega, sem pular nem voltar etapas. {@code ENTREGUE} e {@code CANCELADO} são finais.
 */
public enum OrderStatus {
    RECEBIDO,
    EM_PREPARO,
    SAIU_PARA_ENTREGA,
    ENTREGUE,
    CANCELADO;

    public List<OrderStatus> allowedTransitions() {
        return switch (this) {
            case RECEBIDO -> List.of(EM_PREPARO, CANCELADO);
            case EM_PREPARO -> List.of(SAIU_PARA_ENTREGA, CANCELADO);
            case SAIU_PARA_ENTREGA -> List.of(ENTREGUE);
            case ENTREGUE, CANCELADO -> List.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
