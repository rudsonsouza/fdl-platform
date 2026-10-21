package com.fdl.platform.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Id, status e total são definidos pelo servidor: enviá-los resulta em {@code 400 MALFORMED_REQUEST}. */
@Schema(description = "Dados para criar um pedido. `id`, `status` e `totalCents` são calculados pelo servidor.")
public record CreateOrderRequest(
        @NotNull(message = "é obrigatório") @Valid CustomerRequest customer,

        @NotEmpty(message = "deve ter ao menos 1 item")
        @Size(max = 50, message = "deve ter no máximo 50 itens") List<@NotNull(message = "não pode ser nulo") @Valid OrderItemRequest> items,

        @NotNull(message = "é obrigatório") @Valid AddressRequest deliveryAddress) {
}
