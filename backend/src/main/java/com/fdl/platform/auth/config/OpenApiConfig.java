package com.fdl.platform.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI fdlOpenApi() {
        var problem = new Schema<>()
                .type("object")
                .description("Problem Details (RFC 9457) com campos extras da API")
                .addProperty("type", new Schema<>().type("string").example("urn:fdl:problem:validation-error"))
                .addProperty("title", new Schema<>().type("string").example("Dados inválidos"))
                .addProperty("status", new Schema<>().type("integer").example(400))
                .addProperty("detail", new Schema<>().type("string"))
                .addProperty("instance", new Schema<>().type("string").example("/api/v1/auth/register"))
                .addProperty("code", new Schema<>().type("string").example("VALIDATION_ERROR"))
                .addProperty("traceId", new Schema<>().type("string"))
                .addProperty("errors", new Schema<>().type("array").items(new Schema<>().type("object")
                        .addProperty("field", new Schema<>().type("string").example("email"))
                        .addProperty("message", new Schema<>().type("string"))));
        return new OpenAPI()
                .info(new Info().title("Mini Rastreador de Pedidos").version("v1")
                        .description("API REST para registrar pedidos de delivery e acompanhar seu status."))
                .components(new Components()
                        .addSchemas("ProblemDetail", problem)
                        .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }
}
