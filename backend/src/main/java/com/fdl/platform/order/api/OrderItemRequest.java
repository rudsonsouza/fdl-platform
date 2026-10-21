package com.fdl.platform.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Item do pedido")
public record OrderItemRequest(
        @Schema(example = "Pizza margherita")
        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String name,

        @Schema(example = "2", description = "Inteiro de 1 a 99")
        @NotNull(message = "é obrigatória")
        @Min(value = 1, message = "deve ser no mínimo 1")
        @Max(value = 99, message = "deve ser no máximo 99") Integer quantity,

        @Schema(example = "5490", description = "Preço unitário em centavos (0 a 10.000.000)")
        @NotNull(message = "é obrigatório")
        @Min(value = 0, message = "não pode ser negativo")
        @Max(value = 10_000_000, message = "deve ser no máximo 10.000.000") Long unitPriceCents) {
}
