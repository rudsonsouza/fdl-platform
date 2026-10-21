package com.fdl.platform.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciais de login")
public record LoginRequest(
        @Schema(example = "ana@exemplo.com")
        @NotBlank(message = "é obrigatório")
        @Size(max = 254, message = "deve ter no máximo 254 caracteres")
        @Email(regexp = AuthPatterns.EMAIL, message = "deve ser um e-mail válido") String email,

        @Schema(example = "segredo123")
        @NotBlank(message = "é obrigatória") String password) {

    /** O Spring registra o corpo desserializado em DEBUG: a senha nunca pode sair no toString. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
