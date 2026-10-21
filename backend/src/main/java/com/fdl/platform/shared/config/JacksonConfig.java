package com.fdl.platform.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

/**
 * Jackson 3 estrito na borda da API. Os padrões do Spring Boot 4 / Jackson 3 são permissivos em
 * alguns pontos (ex.: campos desconhecidos são ignorados), então cada regra relevante para os
 * cenários {@code 400} é declarada aqui e coberta por teste, em vez de depender do padrão.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictJsonMapper() {
        return builder -> builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                // 1.5 não vira 1 e "2" não vira 2: quantidade e preço só aceitam inteiros JSON.
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
    }
}
