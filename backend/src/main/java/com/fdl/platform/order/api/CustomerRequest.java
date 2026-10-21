package com.fdl.platform.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Cliente que fez o pedido")
public record CustomerRequest(
        @Schema(example = "Maria Silva")
        @NotBlank(message = "é obrigatório")
        @Size(min = 2, max = 100, message = "deve ter entre 2 e 100 caracteres") String name,

        @Schema(example = "11987654321", description = "Somente dígitos: DDD + número (10 ou 11 dígitos)")
        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter 10 ou 11 dígitos, somente números") String phone) {
}
