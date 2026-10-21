package com.fdl.platform.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do token de acesso. Sem {@code FDL_JWT_SECRET} (ou com menos de 32 caracteres,
 * 256 bits) a aplicação não sobe: não existe segredo padrão.
 */
@Validated
@ConfigurationProperties("fdl.jwt")
public record JwtProperties(
        @NotBlank(message = "FDL_JWT_SECRET é obrigatório") @Size(min = 32, message = "FDL_JWT_SECRET precisa ter ao menos 32 caracteres (256 bits)") String secret,
        @NotNull @DefaultValue("PT60M") Duration ttl,
        @NotBlank @DefaultValue("fdl-platform") String issuer) {
}
