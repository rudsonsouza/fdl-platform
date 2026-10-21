package com.fdl.platform.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Endereço de entrega")
public record AddressRequest(
        @Schema(example = "Avenida Paulista")
        @NotBlank(message = "é obrigatória")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String street,

        @Schema(example = "1000")
        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String number,

        @Schema(example = "Apto 12", description = "Opcional")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String complement,

        @Schema(example = "Bela Vista")
        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String neighborhood,

        @Schema(example = "São Paulo")
        @NotBlank(message = "é obrigatória")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres") String city,

        @Schema(example = "SP", description = "Sigla da UF, duas letras maiúsculas")
        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "[A-Z]{2}", message = "deve ter exatamente 2 letras maiúsculas") String state,

        @Schema(example = "01310100", description = "CEP com 8 dígitos, sem hífen")
        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{8}", message = "deve ter exatamente 8 dígitos, sem hífen") String zipCode) {
}
