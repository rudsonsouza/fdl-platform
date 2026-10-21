package com.fdl.platform.shared.config;

import com.fdl.platform.auth.config.CorsProperties;
import com.fdl.platform.auth.config.SecurityProperties;
import com.fdl.platform.shared.error.ProblemDetailsSecurityHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless protegida por JWT (Resource Server). Públicos: cadastro, login, health e a
 * documentação OpenAPI (que pode ser desligada por {@code FDL_SWAGGER_ENABLED}). Os erros de
 * autenticação saem em Problem Details. CSRF fica desligado porque não há cookie de sessão.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailsSecurityHandler errorHandler)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
        cors.setExposedHeaders(List.of("X-Trace-Id", "Location"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
