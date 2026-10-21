package com.fdl.platform.order.api;

import com.fdl.platform.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Novo status do pedido")
public record UpdateStatusRequest(
        @Schema(example = "EM_PREPARO")
        @NotNull(message = "é obrigatório") OrderStatus status) {
}
