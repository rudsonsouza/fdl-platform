package com.fdl.platform.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso Bearer")
public record TokenResponse(
        @Schema(description = "JWT a enviar em Authorization: Bearer <token>") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do token em segundos", example = "3600") long expiresIn) {

    /** O Spring registra o corpo da resposta em DEBUG: o token nunca pode sair no toString. */
    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
