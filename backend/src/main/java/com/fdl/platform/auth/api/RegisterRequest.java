package com.fdl.platform.auth.api;

import com.fdl.platform.shared.validation.Utf8MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de cadastro de um operador")
public record RegisterRequest(
        @Schema(example = "Ana Souza")
        @NotBlank(message = "é obrigatório")
        @Size(min = 2, max = 100, message = "deve ter entre 2 e 100 caracteres") String name,

        @Schema(example = "ana@exemplo.com")
        @NotBlank(message = "é obrigatório")
        @Size(max = 254, message = "deve ter no máximo 254 caracteres")
        @Email(regexp = AuthPatterns.EMAIL, message = "deve ser um e-mail válido") String email,

        @Schema(example = "segredo123", description = "8 a 72 caracteres, com ao menos uma letra e um dígito")
        @NotBlank(message = "é obrigatória")
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres")
        @Utf8MaxBytes(72)
        @Pattern(regexp = "(?=.*\\p{L})(?=.*\\d).*", message = "deve conter ao menos uma letra e um dígito") String password) {

    /** O Spring registra o corpo desserializado em DEBUG: a senha nunca pode sair no toString. */
    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=***]";
    }
}
