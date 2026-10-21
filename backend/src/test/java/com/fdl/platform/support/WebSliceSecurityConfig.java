package com.fdl.platform.support;

import com.fdl.platform.auth.config.CorsProperties;
import com.fdl.platform.auth.config.SecurityProperties;
import com.fdl.platform.shared.config.SecurityConfig;
import com.fdl.platform.shared.error.ProblemDetailsSecurityHandler;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Segurança real (SecurityConfig) para os testes de fatia web, com um JwtDecoder falso. */
@TestConfiguration
@Import({SecurityConfig.class, ProblemDetailsSecurityHandler.class})
@EnableConfigurationProperties({CorsProperties.class, SecurityProperties.class})
public class WebSliceSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }
}
